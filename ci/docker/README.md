# Docker Images #

There are two docker images:

1. CentOS 6 based - used for building minimal and omp/gpuv4 targets
2. Ubuntu 14 based - used for omp/gpuv3 targets

The CentOS 6 image cannot be built at the moment, because the devtoolset-3 is no longer available for CentOS 6. This image 
is required by Jenkins (because of backward compatibility). There is the CentOS 6 image present in harbor.h2o.ai repository as 
`harbor.h2o.ai/opsh2oai/h2o-3-xgboost-build-centos6:latest`. This image should be used as base for all other required changes. 

The table with Dockerfiles and their purpose:

|            Dockerfile            |                               Purpose                              |
| -------------------------------- | ------------------------------------------------------------------ |
| Dockerfile-gpu-centos-base       | Builds the base CentOS 6 image - **cannot be built at the moment** |
| Dockerfile-gpu-centos6-python3.7 | Adds the Python 3.7 to the CentOS 6 base                           |
| Dockerfile-gpu-ubuntu14          | Build the Ubuntu 14 image                                          |

If you need to build the CentOS based image, there are two options:

1. pull the `harbor.h2o.ai/opsh2oai/h2o-3-xgboost-build-centos6:latest` and extend this image. Do not forget to commit the Dockerfile used to create the new image.
2. provide the `--build-arg FROM_IMAGE=nvidia/cuda:8.0-devel-centos7` flag while building the image - this will create a CentOS 7 based image, which can be built successfully. This option is usefull only for local dev/debugging, Jenkins still need to be able to build xgboost.
