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

  @Override
  public void write(Kryo kryo, Output output) {
    try {
      byte[] serObj = this.toByteArray();
      int serObjSize = serObj.length;
      System.out.println("==== serialized obj size " + serObjSize);
      output.writeInt(serObjSize);
      output.write(serObj);
    } catch (XGBoostError ex) {
      ex.printStackTrace();
      logger.error(ex.getMessage());
    }
  }

  @Override
  public void read(Kryo kryo, Input input) {
    try {
      int serObjSize = input.readInt();
      System.out.println("==== the size of the object: " + serObjSize);
      byte[] bytes = new byte[serObjSize];
      input.readBytes(bytes);
      initFromBytes(bytes);
    } catch (XGBoostError ex) {
      ex.printStackTrace();
      logger.error(ex.getMessage());
    }
  }


}
