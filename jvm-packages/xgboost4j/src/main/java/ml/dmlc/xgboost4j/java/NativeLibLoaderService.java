/*
 Copyright (c) 2014 by Contributors

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */
package ml.dmlc.xgboost4j.java;

import java.util.ServiceLoader;

class NativeLibLoaderService {

  private static final String LOADER_NAME = System.getProperty("sys.xgboost.jni.loader");

  private static NativeLibLoaderService service;
  private ServiceLoader<INativeLibLoader> serviceLoader;

  private NativeLibLoaderService() {
    serviceLoader = ServiceLoader.load(INativeLibLoader.class);
  }

  static synchronized NativeLibLoaderService getInstance() {
    if (service == null) {
      service = new NativeLibLoaderService();
    }
    return service;
  }

  INativeLibLoader createLoader() {
    if (LOADER_NAME != null) {
      return findLoaderByName(LOADER_NAME);
    } else {
      return findLoaderByPriority();
    }
  }

  private INativeLibLoader findLoaderByName(String name) {
    for (INativeLibLoader nlLoader: serviceLoader) {
      if (name.equals(nlLoader.name())) {
        return nlLoader;
      }
    }
    throw new IllegalStateException(
            "Unable to find specified Native Lib Loader (name=" + name + ").");
  }

  private INativeLibLoader findLoaderByPriority() {
    INativeLibLoader selectedLoader = null;
    int maxPriority = Integer.MIN_VALUE;
    for (INativeLibLoader nlLoader : serviceLoader) {
      int priority = nlLoader.priority();
      if (priority > maxPriority) {
        selectedLoader = nlLoader;
        maxPriority = priority;
      }
    }
    if (selectedLoader == null) {
      throw new IllegalStateException("Unable to find any Native Lib Loaders.");
    }
    return selectedLoader;
  }

}
