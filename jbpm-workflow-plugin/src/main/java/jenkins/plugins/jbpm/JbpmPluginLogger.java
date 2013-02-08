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

package jenkins.plugins.jbpm;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import hudson.model.BuildListener;

/**
 * 
 * Provides simple logging functionality to the whole plugin.
 * 
 * @author Jiri Svitak
 * 
 */
public class JbpmPluginLogger {

    private static final Logger logger = LoggerFactory.getLogger(JbpmPluginLogger.class);
    private static BuildListener listener;


    public static synchronized void setListener(BuildListener listener) {
        JbpmPluginLogger.listener = listener;
    }
    
    private static String getStackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        return sw.toString();
    }
    
    public static synchronized void info(Throwable t) {
        info(getStackTrace(t));
    }

    public static synchronized void info(String message) {
        logger.info(message);
        listener.getLogger().println("INFO: " + message);
    }
    
    public static synchronized void debug(Throwable t) {
        debug(getStackTrace(t));
    }
    
    public static synchronized void debug(String message) {
        logger.debug(message);
        listener.getLogger().println("DEBUG: " + message);
    }
    
    public static synchronized void error(Throwable t) {
        error(getStackTrace(t));
    }
    
    public static synchronized void error(String message) {
        logger.error(message);
        listener.getLogger().println("ERROR: " + message);
    }
    
    public static synchronized void warn(Throwable t) {
        warn(getStackTrace(t));
    }
    
    public static synchronized void warn(String message) {
        logger.warn(message);
        listener.getLogger().println("WARN: " + message);
    }
    
}
