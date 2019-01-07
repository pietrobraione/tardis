# TARDIS <img width="20%" src="img/TARDIS.png">

## About

TARDIS (meTAheuRistically-driven DynamIc Symbolic execution) is an automatic test case generator for Java programs, aimed at achieving high branch coverage. It leverages a technique called dynamic symbolic (a.k.a. "concolic") execution, to alternate symbolic execution performed with the symbolic executor [JBSE](https://pietrobraione.github.io/jbse/), with test case generation, performed by finding solutions to symbolic execution path constraints with the test case generator [EvoSuite](http://www.evosuite.org/).

TARDIS aims at preserving the main advantages of [SUSHI](https://github.com/pietrobraione/sushi) and, at the same time, improving performance when information on invariants is missing.
