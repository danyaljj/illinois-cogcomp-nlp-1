## Run tests

There are two tests embedded in `AllTest.java`

`simpleClassifierTest()` reproduces the result of LOCAL TACREC Search

`testWithConstraints()` reproduces the result of INFERENCE TACREC Search

In order to run the tests, 

`mvn lbjava:generate` to generate the LBJ Classifiers.

Then modify the data path variables within the two functions in `AllTest.java`

Then modify the main function of `AllTest.java`