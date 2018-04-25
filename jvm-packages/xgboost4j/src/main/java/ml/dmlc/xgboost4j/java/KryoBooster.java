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

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoSerializable;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Map;

@SuppressWarnings("unused")
public class KryoBooster extends Booster implements KryoSerializable {

  private static final Log logger = LogFactory.getLog(KryoBooster.class);

  KryoBooster(Map<String, Object> params, DMatrix[] cacheMats) throws XGBoostError {
    super(params, cacheMats, true);
  }

  KryoBooster() {
    super(true);
  }

  @Override
  public void write(Kryo kryo, Output output) {
    byte[] serObj;
    try {
      serObj = this.toByteArray();
    } catch (XGBoostError ex) {
      throw new RuntimeException("Booster serialization failed", ex);
    }
    int serObjSize = serObj.length;
    logger.debug("==== serialized obj size " + serObjSize);
    output.writeInt(serObjSize);
    output.write(serObj);
  }

  @Override
  public void read(Kryo kryo, Input input) {
    int serObjSize = input.readInt();
    logger.debug("==== the size of the object: " + serObjSize);
    byte[] bytes = new byte[serObjSize];
    input.readBytes(bytes);
    try {
      initFromBytes(bytes);
    } catch (XGBoostError ex) {
      throw new RuntimeException("Booster deserialization failed", ex);
    }
  }

}
