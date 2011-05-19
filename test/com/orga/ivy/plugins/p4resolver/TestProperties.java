/*
 * Copyright 2011 Felix Drueke
 * Copyright 2008 Last.fm
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.orga.ivy.plugins.p4resolver;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Singleton class to hold the contents of a "test.properties" file located on the CLASSPATH. Used for configuring unit
 * tests.
 * 
 * @author adrian
 */
public class TestProperties {

  private static TestProperties instance = new TestProperties(); // singleton instance
  private Properties properties = new Properties();

  public static final String PROPERTY_P4_HOST = "p4.host";
  public static final String PROPERTY_P4_PORT = "p4.port";
  public static final String PROPERTY_P4_USERNAME = "p4.username";
  //public static final String PROPERTY_P4_PASSWORD = "p4.password";
  public static final String PROPERTY_P4_REPOPATH = "p4.repopath";
  public static final String PROPERTY_ANT_MESSAGE_OUTPUT_LEVEL = "ant.message.output.level";

  /**
   * Private constructor to prevent external instantiation.
   */
  private TestProperties() {
    try {
      //InputStream is = ClassLoader.getSystemClassLoader().getResourceAsStream("test.properties");
    	InputStream is = TestProperties.class.getResourceAsStream("test.properties");
      if (is == null) {
        throw new RuntimeException("Could not find test.properties on the classpath");
      }
      properties.load(is);
    } catch (IOException e) {
      throw new RuntimeException("Error loading properties from test.properties", e);
    }
  }

  /**
   * Method for returning singleton instance.
   * 
   * @return The singleton instance.
   */
  public static TestProperties getInstance() {
    return instance;
  }

  /**
   * Gets the property identified by the passed key.
   * 
   * @param key The property key.
   * @return The property value, or null if no value could be found.
   */
  public String getProperty(String key) {
    return properties.getProperty(key);
  }

  /**
   * Gets the property identified by the passed key.
   * 
   * @param key The property key.
   * @param defaultValue Value to use for property if not found.
   * @return The property value, or the default if no value could be found.
   */
  public String getProperty(String key, String defaultValue) {
    String value = properties.getProperty(key);
    return value == null ? defaultValue : value;
  }
  
}
