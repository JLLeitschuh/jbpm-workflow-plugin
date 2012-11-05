/*
 * The MIT License
 *
 * Copyright (c) 2012, Jiri Svitak
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.jbpm;

import hudson.model.Hudson;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.util.Properties;

import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

import org.drools.KnowledgeBase;
import org.drools.KnowledgeBaseFactory;
import org.drools.builder.KnowledgeBuilder;
import org.drools.builder.KnowledgeBuilderConfiguration;
import org.drools.builder.KnowledgeBuilderFactory;
import org.drools.builder.ResourceType;
import org.drools.core.util.StringUtils;
import org.drools.io.ResourceFactory;
import org.drools.persistence.jpa.JPAKnowledgeService;
import org.drools.runtime.Environment;
import org.drools.runtime.EnvironmentName;
import org.drools.runtime.StatefulKnowledgeSession;
import org.slf4j.LoggerFactory;

public class SessionUtil {
    
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(SessionUtil.class); 
    
    public static KnowledgeBase getKnowledgeBase(String url) {
        Properties droolsProperties = new Properties();
        droolsProperties.setProperty("drools.dialect.java.compiler", "JANINO");
        KnowledgeBuilderConfiguration config = KnowledgeBuilderFactory
                .newKnowledgeBuilderConfiguration(droolsProperties);

        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory
                .newKnowledgeBuilder(config);

        Authenticator.setDefault(GuvnorAuthenticator.getInstance());

        kbuilder.add(ResourceFactory.newUrlResource(url), ResourceType.BPMN2);
        if (kbuilder.hasErrors()) {
            PluginLogger.error(kbuilder.getErrors().toString());
            return null;
        }

        KnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
        kbase.addKnowledgePackages(kbuilder.getKnowledgePackages());
        return kbase;
    }

    public static StatefulKnowledgeSession getStatefulKnowledgeSession(
            KnowledgeBase kbase) {
        StatefulKnowledgeSession ksession = null;
        if ("true".equalsIgnoreCase(PropertiesManager.getPluginProperties().getProperty(
                "persistence.enabled", "false"))) {
            int ksessionId = getPersistedSessionId(System.getProperty("jboss.server.temp.dir"));
            ksession = getPersistedSession(kbase, ksessionId);
            persistSessionId(System.getProperty("jboss.server.temp.dir"), ksessionId);

        } else {
            ksession = kbase.newStatefulKnowledgeSession();
        }

        return ksession;
    }
    
    private static StatefulKnowledgeSession getPersistedSession(KnowledgeBase kbase, int ksessionId) {
        EntityManagerFactory emf = Persistence
                .createEntityManagerFactory("org.jbpm.persistence.jpa");
        Environment env = KnowledgeBaseFactory.newEnvironment();
        env.set(EnvironmentName.ENTITY_MANAGER_FACTORY, emf);
        env.set(EnvironmentName.TRANSACTION_MANAGER,
                com.arjuna.ats.jta.TransactionManager.transactionManager());
        
        boolean createNewKnowledgeSession = true;
        StatefulKnowledgeSession ksession = null;

        if (ksessionId > 0) { 
            createNewKnowledgeSession = false;
            try {
                logger.debug("Loading knowledge session with id " + ksessionId );
                ksession = JPAKnowledgeService.loadStatefulKnowledgeSession(ksessionId, kbase, null, env);
            } catch (RuntimeException e) {
                e.printStackTrace();
                logger.error("Error loading knowledge session : " + e.getMessage());
                if (e instanceof IllegalStateException) {
                    Throwable cause = ((IllegalStateException) e).getCause();
                    if (cause instanceof InvocationTargetException) {
                        cause = cause.getCause();
                        String exceptionMsg = "Could not find session data for id " + ksessionId;
                        if (cause != null && exceptionMsg.equals(cause.getMessage())) {
                            createNewKnowledgeSession = true;
                        } 
                    } 
                } 

                if (! createNewKnowledgeSession) { 
                    String exceptionMsg = e.getMessage();                    
                    if( e.getCause() != null && ! StringUtils.isEmpty(e.getCause().getMessage()) ) { 
                        exceptionMsg = e.getCause().getMessage();
                    }
                    logger.error("Error loading session data: " + exceptionMsg );
                    throw e;
                }
            }
        }

        if( createNewKnowledgeSession ) { 
            env = KnowledgeBaseFactory.newEnvironment();
            env.set(EnvironmentName.ENTITY_MANAGER_FACTORY, emf);
            ksession = JPAKnowledgeService.newStatefulKnowledgeSession(kbase, null, env);
            ksessionId = ksession.getId();
            logger.debug("Created new knowledge session with id " + ksessionId); 
        }
        
        return JPAKnowledgeService.newStatefulKnowledgeSession(kbase,
                null, env);
        
    }

    private static class PropertiesManager {

        private static Properties pluginProperties;

        public static Properties getPluginProperties() {
            if (pluginProperties == null) {
                Properties pluginProperties = new Properties();
                try {
                    InputStream in;
                    //in = new FileInputStream("plugin.properties");
                    in = Hudson.getInstance().getPluginManager().uberClassLoader.getResourceAsStream("plugin.properties");
                    pluginProperties.load(in);
                    in.close();
                } catch (FileNotFoundException e) {
                    logger.warn("Cannot find plugin.properties file: " + e.getMessage());
                } catch (IOException e) {
                    logger.warn("Cannot open plugin.properties file: " + e.getMessage());
                }
            }
            return pluginProperties;
        }
    }

    private static class GuvnorAuthenticator extends Authenticator {

        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            Properties pluginProperties = PropertiesManager
                    .getPluginProperties();
            return new PasswordAuthentication(pluginProperties.getProperty(
                    "guvnor.user", "admin"), pluginProperties.getProperty(
                    "guvnor.password", "admin").toCharArray());
        }

        public static GuvnorAuthenticator getInstance() {
            return new GuvnorAuthenticator();
        }
    }
    
    private static int getPersistedSessionId(String location) {
        File sessionIdStore = new File(location + File.separator + "jbpmSessionId.ser");
        if (sessionIdStore.exists()) {
            Integer knownSessionId = null; 
            FileInputStream fis = null;
            ObjectInputStream in = null;
            try {
                fis = new FileInputStream(sessionIdStore);
                in = new ObjectInputStream(fis);
                
                knownSessionId = (Integer) in.readObject();
                
                return knownSessionId.intValue();
                
            } catch (Exception e) {
                return 0;
            } finally {
                if (fis != null) {
                    try {
                        fis.close();
                    } catch (IOException e) {
                    }
                }
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                    }
                }
            }
            
        } else {
            return 0;
        }
    }
    
    private static void persistSessionId(String location, int ksessionId) {
        if (location == null) {
            return;
        }
        FileOutputStream fos = null;
        ObjectOutputStream out = null;
        try {
            fos = new FileOutputStream(location + File.separator + "jbpmSessionId.ser");
            out = new ObjectOutputStream(fos);
            out.writeObject(Integer.valueOf(ksessionId));
            out.close();
        } catch (IOException ex) {
            logger.warn("Error when persisting known session id", ex);
            ex.printStackTrace();
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                }
            }
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                }
            }
        }
    }
}
