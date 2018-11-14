# TARDIS

## About

TARDIS is an automatic test case generator for Java programs, aimed at (high) branch coverage. It leverages a technique called 
"concolic" execution to alternate symbolic execution, performed with the symbolic executor [JBSE](http://pietrobraione.github.io/jbse/), with test case generation, performed by finding solution to symbolic execution path constraints with the test case generator [EvoSuite](http://www.evosuite.org/).

TARDIS aims at preserving the main advantage of SUSHI and, at the same time, improving performance when information on invariants is missing.

