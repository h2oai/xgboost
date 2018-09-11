package ml.dmlc.xgboost4j.java.util;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class BigDenseMatrixTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

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
        matrix.dispose();
    }
  }

  @Test
  public void testLargeMatrix() {
    Assume.assumeTrue(Boolean.getBoolean("ENABLE_LARGE_TESTS"));

    final int nrow = Integer.MAX_VALUE;
    final int ncol = 2;
    BigDenseMatrix matrix = null;
    try {
      matrix = new BigDenseMatrix(nrow, ncol);

      long val = 0;
      for (int i = 0; i < nrow; i++)
        for (int j = 0; j < ncol; j++)
          matrix.set(i, j, val++);

      Assert.assertEquals((float) val, matrix.get((long) nrow * ncol - 1), 0);
    } finally {
      if (matrix != null)
        matrix.dispose();
    }
  }

  @Test
  public void testMaxSize() {
    thrown.expectMessage("Matrix too large; matrix size cannot exceed 2305843009213693951");
    new BigDenseMatrix(Integer.MAX_VALUE, Integer.MAX_VALUE);
  }

}