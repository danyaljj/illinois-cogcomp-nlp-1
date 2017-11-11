## Run tests

There are two tests embedded in `AllTest.java`

`simpleClassifierTest()` reproduces the result of LOCAL TACREC Search

`testWithConstraints()` reproduces the result of INFERENCE TACREC Search

In order to run the tests, 

`mvn lbjava:generate` to generate the LBJ Classifiers.

Then modify the data path variables within the two functions in `AllTest.java`

Then modify the main function of `AllTest.java`

## Understanding the results

Note that the two functions will produce both "Accuracy" and "F1"

The paper used "Accuracy", which is computed by the correct numbers of instances (including no relation) divided by the total number of instances (including no relation)

F1 is measure by taking out the no relation instances. (Not useful if you are comparing results with the paper).
