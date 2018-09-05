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
package ml.dmlc.xgboost4j.java.util;

/**
 * Off-heap implementation of a Dense Matrix, matrix size is only limited by the amount of the available memory
 */
public final class BigDenseMatrix {

  private final static int FLOAT_BYTE_SIZE = 4;

  public final long nrow;
  public final long ncol;
  public final long address;

  public BigDenseMatrix(long nrow, long ncol) {
    this.nrow = nrow;
    this.ncol = ncol;
    this.address = UtilUnsafe.UNSAFE.allocateMemory(ncol * nrow * FLOAT_BYTE_SIZE);
  }

  public final void set(long idx, float val) {
    UtilUnsafe.UNSAFE.putFloat(address + idx * FLOAT_BYTE_SIZE, val);
  }

  public final void set(long i, long j, float val) {
    set(i * ncol + j, val);
  }

  public final float get(long idx) {
    return UtilUnsafe.UNSAFE.getFloat(address + idx * FLOAT_BYTE_SIZE);
  }

  public final float get(long i, long j) {
    return get(i * ncol + j);
  }

  public final void dispose() {
    UtilUnsafe.UNSAFE.freeMemory(address);
  }

}
