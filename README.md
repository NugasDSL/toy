# Toy - A Total ordering Optimistic sYstem
## Introduction
### Abstract
Toy is a total ordering optimistic system targeting permissioned Blockchains. It assumes an optimal conditions and so amortized the 
the cost of handling failures among all rounds, creating a robust, scalable and highly efficient Blockchain platform.

### System Model
Toy assume a Byzantine partial synchronous environment in which the number of faulty nodes, _f_ is less then third of the nodes.
_Partial synchronous_ means that after an unknown time _t_ there is an unknown upper bound _k_ on the messages transfer delays.

### Papers
This work is an implementation of the TOY algorithm described in TBA

## Getting Started
### Installation (for Ubuntu)
1. Install [Maven](https://maven.apache.org/)
    * `sudo apt install maven` 
1. Download the project:
    * `git pull https://github.com/NugasDSL/toy.git`
    * Or via this [link](https://github.com/NugasDSL/toy/archive/master.zip)
1. Go to the project directory and run:
    * `mvn package`
1. `lib/toy-core-1.0.jar` will be created under the project directory
1. You may now use this jar to create a _TOY_ server

## Configurations
    
    