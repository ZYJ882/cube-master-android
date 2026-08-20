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
        /** CFOP 求解耗时（毫秒）；使用 System.nanoTime 以兼容 minSdk 24 且不受系统时钟调整影响。 */
        public long SolverTimeMillis = 0L;

        public String Solution = "";

        public FridrichSolver(String ScramledCube)
        {
            this.Cube = ScramledCube.toCharArray();
        }

        public int Solve()
        {
            long startNanos = System.nanoTime();

            Cross.Solve(this);

            F2L.Solve(this);

            OLL.Solve(this);

            PLL.Solve(this);

            if (Arrays.equals(this.Cube, Constants.SolvedCube))
            {
                Tools.OptimizeSolution(this); //Removes redundant moves like "U U'" and reduces "R R2" to "R'"

                this.SolverTimeMillis = (System.nanoTime() - startNanos) / 1_000_000L;

                this.IsSolved = true;

                return 1; //Success
            }
            else
                return -9; //Unknown Error
        }
    }
