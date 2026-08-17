/*
 * Derived from CubeXdotNET Rubik's Cube Solver by Divins Mathew (2017).
 * SPDX-License-Identifier: MIT
 * Original source: https://github.com/divinsmathew/CubeXdotNet-Rubiks-Cube-Solver
 * The complete MIT license is retained in LICENSE-MIT-CubeX.txt.
 */
package com.manus.cubemaster.solver.cfop;

import java.util.Arrays;


    public final class FridrichSolver
    {
        public char[] Cube = "".toCharArray();
        public int Length = 0;
        public boolean IsSolved = false;
        public int ErrorCode = 0;
        public java.time.Duration SolverTime = java.time.Duration.ZERO;

        public String Solution = "";

        public FridrichSolver(String ScramledCube)
        {
            this.Cube = ScramledCube.toCharArray();
        }

        public int Solve()
        {
            java.time.Instant StartTime = java.time.Instant.now();

            Cross.Solve(this);

            F2L.Solve(this);

            OLL.Solve(this);

            PLL.Solve(this);

            if (Arrays.equals(this.Cube, Constants.SolvedCube))
            {
                Tools.OptimizeSolution(this); //Removes redundant moves like "U U'" and reduces "R R2" to "R'"

                this.SolverTime = java.time.Duration.between(StartTime, java.time.Instant.now());

                this.IsSolved = true;

                return 1; //Success
            }
            else
                return -9; //Unknown Error
        }
    }
