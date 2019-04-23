# XGBoost Build Instructions: #

- **3 different flavours:**
	1. minimal - no OMP and no GPU support
	2. omp - with OMP but no GPU support
	3. gpu - with GPU support

	- please note that for `omp` and `gpu` flavours, there are two possible versions of libgomp - v3 and v4, therefore `omp` and `gpu` flavours have `v3` or `v4` suffix to denote, which libgomp version is required
- **artifacts:**
	- `.so` or `.dylib`
		- XGBoost library files
	- `.whl`
		- Python wheel, contains the `.so` or `.dylib`
	- `.jar`
		- used mostly by H2O-3
		- Java JAR, contains the `.so` or `.dylib`
- **supported platforms:**
	- **Mac OS X** - only minimal is tested
	- **Linux** - tested on CentOS 6 and 7 and Ubuntu 14 and 16

## Checkout ##
Use the following snippet to checkout `h2oai/xgboost` repo and its submodules.

```[bash]
git clone https://github.com/h2oai/xgboost.git

cd xgboost

git checkout h2o3
git submodule update --init --recursive
```

## Build Java Library ##
- **prerequisities:**
	- mvn
	- JDK 8
- **step 1/1** - build library

```[bash]
cd jvm-packages/xgboost4j
mvn \
	-Dmaven.test.skip=true \
	-DskipTests \
	-Dexclude.xgboost.lib=true \
	clean package
```

## Building on Mac OS X ##
- **prerequisities:**
	- cmake
	- mvn
- **step 0/3** - clean
	- `make -f ci/Makefile.jenkins mrproper`
- **step 1/3** - build XGBoost lib:
	-  `make -f ci/Makefile.jenkins build_minimal`
	-  artifacts can be found in `lib/` dir
- **step 2/3** - Python wheel (for current Python version):
	- `make -f ci/Makefile.jenkins whl_minimal`
	-  artifacts can be found in `ci-build/` dir
- **step 3/3** - Java JAR:
	- `make -f ci/Makefile.jenkins TARGET_OS=osx jar_minimal jar_package_minimal`
	- artifacts can be found in `ci-build/xgboost4j-osx-minimal/target/`
- building in one line:
	- `make -f ci/Makefile.jenkins TARGET_OS=osx mrproper build_minimal whl_minimal jar_minimal jar_package_minimal`

## Building for Linux ##
- **prerequisities:**
	- build is executed in docker containers, at first build docker images:

```[bash]
cd ci/docker
docker build -t harbor.h2o.ai/opsh2oai/h2o-3-xgboost-build-centos --build-arg FROM_IMAGE=nvidia/cuda:8.0-devel-centos7 -f Dockerfile-gpu-centos-base .
docker build -t harbor.h2o.ai/opsh2oai/h2o-3-xgboost-build-ubuntu14 -f Dockerfile-gpu-ubuntu14 .
cd ../../
```
	
- there are two images:
	- CentOS one is used for libgomp v4 flavours
	- Ubuntu one is used for libgomp v3 flavours
- following steps use `ompv4`, if you wish to build `minimal`,`ompv3` or `gpuv*`, replace `ompv4` with desired flavour
- **step 0/3** - clean
	- `make -f ci/Makefile.jenkins mrproper`
- **step 1/3** - build XGBoost lib:
	-  `make -f ci/Makefile.jenkins build_ompv4_in_docker`
	-  artifacts can be found in `lib/` dir
- **step 2/3** - Python wheels for Python 2.7, 3.5, 3.6 and 3.7:
	- `make -f ci/Makefile.jenkins whls_ompv4_in_docker`
	-  artifacts can be found in `ci-build/` dir
- **step 3/3** - Java JAR:
	- `make -f ci/Makefile.jenkins TARGET_OS=linux jar_ompv4_in_docker jar_package_ompv4_in_docker`
	- artifacts can be found in `ci-build/xgboost4j-linux-ompv4/target/`
- building in one line:
	- `make -f ci/Makefile.jenkins TARGET_OS=linux mrproper build_ompv4_in_docker whls_ompv4_in_docker jar_ompv4_in_docker jar_package_ompv4_in_docker`

## Building Behind Proxy ##
If need `mvn` in docker containers to use http/https proxy, add `MVN_OPTS="-DproxySet=true -Dhttp.proxyHost=yourHttpProxyHost -Dhttp.proxyPort=yourHttpProxyPort -Dhttps.proxyHost=yourHttpsProxyHost -Dhttps.proxyPort=yourHttpsProxyPort"` option to the to the `make` calls.