package ml.dmlc.xgboost4j.java.util;

import org.junit.Assert;
import org.junit.Test;

public class BigDenseMatrixTest {

  @Test
  public void testMatrix() {
    final int nrow = 5;
    final int ncol = 7;
    BigDenseMatrix matrix = null;
    try {
      matrix = new BigDenseMatrix(nrow, ncol);

      int val = 0;
      for (int i = 0; i < nrow; i++)
        for (int j = 0; j < ncol; j++)
          matrix.set(i, j, val++);

      for (int i = 0; i < nrow * ncol; i++)
        Assert.assertEquals(i, matrix.get(i), 0);
    } finally {
      if (matrix != null)
        matrix.dispose();;
    }
  }

}