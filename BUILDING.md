# XGBoost Build Instructions: #

- **3 different flavours:**
	1. minimal - no OMP and no GPU support
	2. omp - with OMP but no GPU support
	3. gpu - with GPU support

	- please note that for `omp` and `gpu` flavours

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
	- **Linux** - tested on CentOS 7 and Ubuntu 16

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
	- `make -f ci/Makefile.jenkins build_minimal`
	-  artifacts can be found in `lib/` dir
- **step 2/3** - Python wheel (for current Python version):
	- `make -f ci/Makefile.jenkins whl_minimal`
	-  artifacts can be found in `ci-build/` dir
- **step 3/3** - Java JAR:
	- `make -f ci/Makefile.jenkins TARGET_OS=osx write_version jar_minimal jar_package_minimal`
	- artifacts can be found in `ci-build/xgboost4j-osx-minimal/target/`
- building in one line:
	- `make -f ci/Makefile.jenkins TARGET_OS=osx mrproper write_version build_minimal whl_minimal jar_minimal jar_package_minimal`

## Building for Linux ##
- **prerequisities:**
	- build is executed in docker containers
	
- following steps use `ompv4`, if you wish to build `minimal` or `gpuv*`, replace `ompv4` with desired flavour
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
	- `make -f ci/Makefile.jenkins TARGET_OS=linux mrproper all_ompv4`

## Building Behind Proxy ##
If need `mvn` in docker containers to use http/https proxy, add `MVN_OPTS="-DproxySet=true -Dhttp.proxyHost=yourHttpProxyHost -Dhttp.proxyPort=yourHttpProxyPort -Dhttps.proxyHost=yourHttpsProxyHost -Dhttps.proxyPort=yourHttpsProxyPort"` option to the to the `make` calls.