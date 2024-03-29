# Beacon Chain Java
Ethereum 2.0 Beacon chain client. Someday, definitely, it will be a fully featured Serenity client. We are working to get there. Currently there is no p2p and, hence, there is no cross client communication.
 
## Ethereum 2.0?
Yes, Ethereum Foundation, community and other interested parties are developing successor of [Ethereum](https://ethereum.org/) without cons :).
New blockchain starts from [Phase 0](https://github.com/ethereum/eth2.0-specs/blob/master/specs/core/0_beacon-chain.md) also known as Beacon chain, a core of Ethereum 2.0 blockchain. Phase 1 will be the next one and so on.

## Develop
If you want to take part in Ethereum 2.0 development and use our code, we split everything into several modules, so anyone could easily take only the needed part. To dig into module goals, check [settings.gradle](settings.gradle). 

You can build one module jar with gradle `assemble` task executed on desired module:
```bash
./gradlew :crypto:assemble
cp crypto/build/libs/beacon-crypto-0.1.0.jar <jar-destination>
``` 

## Simulator
Despite lacking a network stack there is already something that everybody can play with, a standalone Beacon chain simulator.

Use [Installation guide](https://github.com/harmony-dev/beacon-chain-java/wiki/Beacon-chain-simulator#installation-guide) to install simulator. For additional details check out [Run simulation](https://github.com/harmony-dev/beacon-chain-java/wiki/Beacon-chain-simulator#run-simulation) section.

## Contribution guideline
Thank you for joining our efforts to drive Ethereum forward! 
We are not very strict on requirements but your code should help us to reach our goal, it should be easy to get it, understand the idea, and it should be feasible to review it. Also we are trying to match [Google code style](https://google.github.io/styleguide/javaguide.html) but we don't like it. Feel free to choose any [issue](https://github.com/harmony-dev/beacon-chain-java/issues) and ask how to do it better.  

## Links
[Ethereum 2.0 specs](https://github.com/ethereum/eth2.0-specs)  
[Vitalik Buterin on DevCon4 about Ethereum 2.0](https://slideslive.com/38911602/latest-of-ethereum)
 

## Licensing
This project is licensed under Apache 2.0 license. You could use it for any commercial, private or open-source project.

## Donations
If you like the project, we could use your donations to fund the development:

`0xF5eFA576ee17A381d798299d10eD397c4dce9BdD`
