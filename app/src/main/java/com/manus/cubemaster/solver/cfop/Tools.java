/*
 * Derived from CubeXdotNET Rubik's Cube Solver by Divins Mathew (2017).
 * SPDX-License-Identifier: MIT
 * Original source: https://github.com/divinsmathew/CubeXdotNet-Rubiks-Cube-Solver
 * The complete MIT license is retained in LICENSE-MIT-CubeX.txt.
 */
package com.manus.cubemaster.solver.cfop;

import java.util.Arrays;


    public final class Tools
    {
        private Tools() { }
        public static int AscConv(int i)
        {
            switch (i)
            {
                case 103: return 2;
                case 111: return 12;
                case 98: return 1;
                case 114: return 4;
                case 121: return 14;
                case 119: return 15;
            }
            return 999;
        }

        public static void OptimizeSolution(FridrichSolver Cube)
        {
            // Deliberately retain raw CFOP moves; stage integrity is more important than cosmetic compression.
        }

        public static int ConjugateEdge(int Edge)
        {
            switch (Edge)
            {
                case 1: return (41);
                case 10: return (37);
                case 19: return (39);
                case 28: return (43);
                case 5: return (12);
                case 14: return (21);
                case 23: return (30);
                case 32: return (3);
                case 7: return (50);
                case 16: return (52);
                case 25: return (48);
                case 34: return (46);
                case 41: return (1);
                case 37: return (10);
                case 39: return (19);
                case 43: return (28);
                case 12: return (5);
                case 21: return (14);
                case 30: return (23);
                case 3: return (32);
                case 50: return (7);
                case 52: return (16);
                case 48: return (25);
                case 46: return (34);
            }
            return 999;
        }

        public static String ydPerm(String algo)
        {
            var alg = algo.toCharArray();
            for (int i = 0; i < alg.length; i++)
            {
                switch (alg[i])
                {
                    case 'R': alg[i] = 'F'; break;
                    case 'B': alg[i] = 'R'; break;
                    case 'L': alg[i] = 'B'; break;
                    case 'F': alg[i] = 'L'; break;
                }
            }
            return new String(alg);
        }

        public static String y2Perm(String algo)
        {
            var alg = algo.toCharArray();
            for (int i = 0; i < alg.length; i++)
            {
                switch (alg[i])
                {
                    case 'R': alg[i] = 'L'; break;
                    case 'B': alg[i] = 'F'; break;
                    case 'L': alg[i] = 'R'; break;
                    case 'F': alg[i] = 'B'; break;
                }
            }
            return new String(alg);
        }

        public static String yPerm(String algo)
        {
            var alg = algo.toCharArray();
            for (int i = 0; i < alg.length; i++)
            {
                switch (alg[i])
                {
                    case 'R': alg[i] = 'B'; break;
                    case 'B': alg[i] = 'L'; break;
                    case 'L': alg[i] = 'F'; break;
                    case 'F': alg[i] = 'R'; break;
                }
            }
            return new String(alg);
        }

        public static void RotateCube(FridrichSolver Cube, int st, int cn, String tag)
{
            int i, fa = 0, dir, tim;
            char a, b, c;
            char[] talg = new char[100];

            if (st == 123 && cn == 123)
                talg = tag.toCharArray();
            else
                switch (st)
                {
                    case 0:
                        switch (cn)
                        {
                            case 0: talg = " U".toCharArray(); break;
                            case 1: talg = " U'".toCharArray(); break;
                            case 2: talg = " U2".toCharArray(); break;
                        }
                        break;

                    case 1: talg = Constants.CrossAlgs[cn].toCharArray(); break;
                    case 2: talg = yPerm(Constants.F2L_Algs[cn]).toCharArray(); break;
                    case 3: talg = y2Perm(Constants.F2L_Algs[cn]).toCharArray(); break;
                    case 4: talg = ydPerm(Constants.F2L_Algs[cn]).toCharArray(); break;
                    case 5: talg = Constants.F2L_Algs[cn].toCharArray(); break;

                    case 6: talg = ydPerm(Constants.OLL_Algs[cn]).toCharArray(); break;
                    case 7: talg = Constants.OLL_Algs[cn].toCharArray(); break;
                    case 8: talg = yPerm(Constants.OLL_Algs[cn]).toCharArray(); break;
                    case 9: talg = y2Perm(Constants.OLL_Algs[cn]).toCharArray(); break;

                    case 10:
                        talg = "U' ".toCharArray();
                        talg = concatChars(talg, Constants.PLL_Algs[cn]);
                        break;
                    case 11:
                        talg = Constants.PLL_Algs[cn].toCharArray();
                        break;
                    case 12:
                        talg = "U ".toCharArray();
                        talg = concatChars(talg, Constants.PLL_Algs[cn]);
                        break;
                    case 13:
                        talg = "U2 ".toCharArray();
                        talg = concatChars(talg, Constants.PLL_Algs[cn]);
                        break;

                    case 14:
                        switch (cn)
                        {
                            case 1: talg = "R".toCharArray(); break;
                            case 2: talg = "R'".toCharArray(); break;
                            case 3: talg = "R2".toCharArray(); break;
                        }
                        break;
                    case 15:
                        switch (cn)
                        {
                            case 1: talg = "U".toCharArray(); break;
                            case 2: talg = "U'".toCharArray(); break;
                            case 3: talg = "U2".toCharArray(); break;
                        }
                        break;
                    case 16:
                        switch (cn)
                        {
                            case 1: talg = "F".toCharArray(); break;
                            case 2: talg = "F'".toCharArray(); break;
                            case 3: talg = "F2".toCharArray(); break;
                        }
                        break;
                    case 17:
                        switch (cn)
                        {
                            case 1: talg = "B".toCharArray(); break;
                            case 2: talg = "B'".toCharArray(); break;
                            case 3: talg = "B2".toCharArray(); break;
                        }
                        break;
                    case 18:
                        switch (cn)
                        {
                            case 1: talg = "L".toCharArray(); break;
                            case 2: talg = "L'".toCharArray(); break;
                            case 3: talg = "L2".toCharArray(); break;
                        }
                        break;
                    case 19:
                        switch (cn)
                        {
                            case 1: talg = "D".toCharArray(); break;
                            case 2: talg = "D'".toCharArray(); break;
                            case 3: talg = "D2".toCharArray(); break;
                        }
                        break;
                }

            i = 0;
            while (true) {

            if (i >= talg.length)
                return;

            if (talg[i] == ' ')
            {
                i++;
                continue;
            }
            else
                if (talg[i] == 'R' || talg[i] == 'U' || talg[i] == 'F' || talg[i] == 'D' || talg[i] == 'B' || talg[i] == 'L')
            {
                switch (Character.toLowerCase(talg[i]))
                {
                    case 'r': fa = 1; break;
                    case 'u': fa = 2; break;
                    case 'f': fa = 3; break;
                    case 'l': fa = 4; break;
                    case 'b': fa = 5; break;
                    case 'd': fa = 6; break;
                }
            }

            try
            {
                if (talg[i + 1] == 39)
                {
                    dir = -1;
                    tim = 1;
                    i++;
                }
                else if (talg[i + 1] == '2')
                {
                    dir = 1;
                    tim = 2;
                    i++;
                }
                else if (talg[i] != 'R' && talg[i] != 'U' && talg[i] != 'F' && talg[i] != 'D' && talg[i] != 'B' && talg[i] != 'L')
                {
                    i++;
                    continue;
                }
                else
                {
                    dir = 1;
                    tim = 1;
                }
            }
            catch (ArrayIndexOutOfBoundsException ignored)
            {
                dir = 1;
                tim = 1;
            }
            //////////////////////////////////////


            //loaded fa,tim and dir;

            switch (fa)
            {
                case 1:
                    switch (tim)
                    {
                        case 1:
                            if (dir == 1)
                            {
                                a = Cube.Cube[8]; b = Cube.Cube[5]; c = Cube.Cube[2];
                                Cube.Cube[8] = Cube.Cube[51]; Cube.Cube[5] = Cube.Cube[52]; Cube.Cube[2] = Cube.Cube[53];
                                Cube.Cube[51] = Cube.Cube[18]; Cube.Cube[52] = Cube.Cube[21]; Cube.Cube[53] = Cube.Cube[24];
                                Cube.Cube[18] = Cube.Cube[38]; Cube.Cube[21] = Cube.Cube[37]; Cube.Cube[24] = Cube.Cube[36];
                                Cube.Cube[38] = a; Cube.Cube[37] = b; Cube.Cube[36] = c;

                                a = Cube.Cube[9]; b = Cube.Cube[10];
                                Cube.Cube[9] = Cube.Cube[15]; Cube.Cube[10] = Cube.Cube[12];
                                Cube.Cube[15] = Cube.Cube[17]; Cube.Cube[12] = Cube.Cube[16];
                                Cube.Cube[17] = Cube.Cube[11]; Cube.Cube[16] = Cube.Cube[14];
                                Cube.Cube[11] = a; Cube.Cube[14] = b;

                                i++; continue;
                            }
                            else if (dir == -1)
                            {
                                a = Cube.Cube[2]; b = Cube.Cube[5]; c = Cube.Cube[8];
                                Cube.Cube[2] = Cube.Cube[36]; Cube.Cube[5] = Cube.Cube[37]; Cube.Cube[8] = Cube.Cube[38];
                                Cube.Cube[36] = Cube.Cube[24]; Cube.Cube[37] = Cube.Cube[21]; Cube.Cube[38] = Cube.Cube[18];
                                Cube.Cube[24] = Cube.Cube[53]; Cube.Cube[21] = Cube.Cube[52]; Cube.Cube[18] = Cube.Cube[51];
                                Cube.Cube[53] = a; Cube.Cube[52] = b; Cube.Cube[51] = c;

                                b = Cube.Cube[10]; a = Cube.Cube[9];
                                Cube.Cube[10] = Cube.Cube[14]; Cube.Cube[9] = Cube.Cube[11];
                                Cube.Cube[14] = Cube.Cube[16]; Cube.Cube[11] = Cube.Cube[17];
                                Cube.Cube[16] = Cube.Cube[12]; Cube.Cube[17] = Cube.Cube[15];
                                Cube.Cube[12] = b; Cube.Cube[15] = a;

                                i++; continue;
                            }
                            break;

                        case 2:
                            a = Cube.Cube[8]; b = Cube.Cube[5]; c = Cube.Cube[2];
                            Cube.Cube[8] = Cube.Cube[51]; Cube.Cube[5] = Cube.Cube[52]; Cube.Cube[2] = Cube.Cube[53];
                            Cube.Cube[51] = Cube.Cube[18]; Cube.Cube[52] = Cube.Cube[21]; Cube.Cube[53] = Cube.Cube[24];
                            Cube.Cube[18] = Cube.Cube[38]; Cube.Cube[21] = Cube.Cube[37]; Cube.Cube[24] = Cube.Cube[36];
                            Cube.Cube[38] = a; Cube.Cube[37] = b; Cube.Cube[36] = c;

                            a = Cube.Cube[9]; b = Cube.Cube[10];
                            Cube.Cube[9] = Cube.Cube[15]; Cube.Cube[10] = Cube.Cube[12];
                            Cube.Cube[15] = Cube.Cube[17]; Cube.Cube[12] = Cube.Cube[16];
                            Cube.Cube[17] = Cube.Cube[11]; Cube.Cube[16] = Cube.Cube[14];
                            Cube.Cube[11] = a; Cube.Cube[14] = b;

                            a = Cube.Cube[8]; b = Cube.Cube[5]; c = Cube.Cube[2];
                            Cube.Cube[8] = Cube.Cube[51]; Cube.Cube[5] = Cube.Cube[52]; Cube.Cube[2] = Cube.Cube[53];
                            Cube.Cube[51] = Cube.Cube[18]; Cube.Cube[52] = Cube.Cube[21]; Cube.Cube[53] = Cube.Cube[24];
                            Cube.Cube[18] = Cube.Cube[38]; Cube.Cube[21] = Cube.Cube[37]; Cube.Cube[24] = Cube.Cube[36];
                            Cube.Cube[38] = a; Cube.Cube[37] = b; Cube.Cube[36] = c;

                            a = Cube.Cube[9]; b = Cube.Cube[10];
                            Cube.Cube[9] = Cube.Cube[15]; Cube.Cube[10] = Cube.Cube[12];
                            Cube.Cube[15] = Cube.Cube[17]; Cube.Cube[12] = Cube.Cube[16];
                            Cube.Cube[17] = Cube.Cube[11]; Cube.Cube[16] = Cube.Cube[14];
                            Cube.Cube[11] = a; Cube.Cube[14] = b;

                            i++; continue;
                    }
                    break;

                case 2:
                    switch (tim)
                    {
                        case 1:
                            if (dir == 1)
                            {
                                a = Cube.Cube[2]; b = Cube.Cube[1]; c = Cube.Cube[0];
                                Cube.Cube[2] = Cube.Cube[11]; Cube.Cube[1] = Cube.Cube[10]; Cube.Cube[0] = Cube.Cube[9];
                                Cube.Cube[11] = Cube.Cube[20]; Cube.Cube[10] = Cube.Cube[19]; Cube.Cube[9] = Cube.Cube[18];
                                Cube.Cube[20] = Cube.Cube[29]; Cube.Cube[19] = Cube.Cube[28]; Cube.Cube[18] = Cube.Cube[27];
                                Cube.Cube[29] = a; Cube.Cube[28] = b; Cube.Cube[27] = c;

                                a = Cube.Cube[42]; b = Cube.Cube[39];
                                Cube.Cube[42] = Cube.Cube[44]; Cube.Cube[39] = Cube.Cube[43];
                                Cube.Cube[44] = Cube.Cube[38]; Cube.Cube[43] = Cube.Cube[41];
                                Cube.Cube[38] = Cube.Cube[36]; Cube.Cube[41] = Cube.Cube[37];
                                Cube.Cube[36] = a; Cube.Cube[37] = b;

                                i++; continue;
                            }
                            else if (dir == -1)
                            {
                                a = Cube.Cube[0]; b = Cube.Cube[1]; c = Cube.Cube[2];
                                Cube.Cube[0] = Cube.Cube[27]; Cube.Cube[1] = Cube.Cube[28]; Cube.Cube[2] = Cube.Cube[29];
                                Cube.Cube[27] = Cube.Cube[18]; Cube.Cube[28] = Cube.Cube[19]; Cube.Cube[29] = Cube.Cube[20];
                                Cube.Cube[18] = Cube.Cube[9]; Cube.Cube[19] = Cube.Cube[10]; Cube.Cube[20] = Cube.Cube[11];
                                Cube.Cube[9] = a; Cube.Cube[10] = b; Cube.Cube[11] = c;

                                a = Cube.Cube[42]; b = Cube.Cube[39];
                                Cube.Cube[42] = Cube.Cube[36]; Cube.Cube[39] = Cube.Cube[37];
                                Cube.Cube[36] = Cube.Cube[38]; Cube.Cube[37] = Cube.Cube[41];
                                Cube.Cube[38] = Cube.Cube[44]; Cube.Cube[41] = Cube.Cube[43];
                                Cube.Cube[44] = a; Cube.Cube[43] = b;

                                i++; continue;
                            }
                            break;

                        case 2:
                            a = Cube.Cube[2]; b = Cube.Cube[1]; c = Cube.Cube[0];
                            Cube.Cube[2] = Cube.Cube[11]; Cube.Cube[1] = Cube.Cube[10]; Cube.Cube[0] = Cube.Cube[9];
                            Cube.Cube[11] = Cube.Cube[20]; Cube.Cube[10] = Cube.Cube[19]; Cube.Cube[9] = Cube.Cube[18];
                            Cube.Cube[20] = Cube.Cube[29]; Cube.Cube[19] = Cube.Cube[28]; Cube.Cube[18] = Cube.Cube[27];
                            Cube.Cube[29] = a; Cube.Cube[28] = b; Cube.Cube[27] = c;

                            a = Cube.Cube[42]; b = Cube.Cube[39];
                            Cube.Cube[42] = Cube.Cube[44]; Cube.Cube[39] = Cube.Cube[43];
                            Cube.Cube[44] = Cube.Cube[38]; Cube.Cube[43] = Cube.Cube[41];
                            Cube.Cube[38] = Cube.Cube[36]; Cube.Cube[41] = Cube.Cube[37];
                            Cube.Cube[36] = a; Cube.Cube[37] = b;

                            a = Cube.Cube[2]; b = Cube.Cube[1]; c = Cube.Cube[0];
                            Cube.Cube[2] = Cube.Cube[11]; Cube.Cube[1] = Cube.Cube[10]; Cube.Cube[0] = Cube.Cube[9];
                            Cube.Cube[11] = Cube.Cube[20]; Cube.Cube[10] = Cube.Cube[19]; Cube.Cube[9] = Cube.Cube[18];
                            Cube.Cube[20] = Cube.Cube[29]; Cube.Cube[19] = Cube.Cube[28]; Cube.Cube[18] = Cube.Cube[27];
                            Cube.Cube[29] = a; Cube.Cube[28] = b; Cube.Cube[27] = c;

                            a = Cube.Cube[42]; b = Cube.Cube[39];
                            Cube.Cube[42] = Cube.Cube[44]; Cube.Cube[39] = Cube.Cube[43];
                            Cube.Cube[44] = Cube.Cube[38]; Cube.Cube[43] = Cube.Cube[41];
                            Cube.Cube[38] = Cube.Cube[36]; Cube.Cube[41] = Cube.Cube[37];
                            Cube.Cube[36] = a; Cube.Cube[37] = b;

                            i++; continue;
                    }
                    break;

                case 3:
                    switch (tim)
                    {
                        case 1:
                            if (dir == 1)
                            {
                                a = Cube.Cube[38]; b = Cube.Cube[41]; c = Cube.Cube[44];
                                Cube.Cube[38] = Cube.Cube[29]; Cube.Cube[41] = Cube.Cube[32]; Cube.Cube[44] = Cube.Cube[35];
                                Cube.Cube[29] = Cube.Cube[47]; Cube.Cube[32] = Cube.Cube[50]; Cube.Cube[35] = Cube.Cube[53];
                                Cube.Cube[47] = Cube.Cube[15]; Cube.Cube[50] = Cube.Cube[12]; Cube.Cube[53] = Cube.Cube[9];
                                Cube.Cube[15] = a; Cube.Cube[12] = b; Cube.Cube[9] = c;

                                a = Cube.Cube[0]; b = Cube.Cube[1];
                                Cube.Cube[0] = Cube.Cube[6]; Cube.Cube[1] = Cube.Cube[3];
                                Cube.Cube[6] = Cube.Cube[8]; Cube.Cube[3] = Cube.Cube[7];
                                Cube.Cube[8] = Cube.Cube[2]; Cube.Cube[7] = Cube.Cube[5];
                                Cube.Cube[2] = a; Cube.Cube[5] = b;

                                i++; continue;
                            }
                            else if (dir == -1)
                            {
                                a = Cube.Cube[38]; b = Cube.Cube[41]; c = Cube.Cube[44];
                                Cube.Cube[38] = Cube.Cube[15]; Cube.Cube[41] = Cube.Cube[12]; Cube.Cube[44] = Cube.Cube[9];
                                Cube.Cube[15] = Cube.Cube[47]; Cube.Cube[12] = Cube.Cube[50]; Cube.Cube[9] = Cube.Cube[53];
                                Cube.Cube[47] = Cube.Cube[29]; Cube.Cube[50] = Cube.Cube[32]; Cube.Cube[53] = Cube.Cube[35];
                                Cube.Cube[29] = a; Cube.Cube[32] = b; Cube.Cube[35] = c;

                                a = Cube.Cube[0]; b = Cube.Cube[1];
                                Cube.Cube[0] = Cube.Cube[2]; Cube.Cube[1] = Cube.Cube[5];
                                Cube.Cube[2] = Cube.Cube[8]; Cube.Cube[5] = Cube.Cube[7];
                                Cube.Cube[8] = Cube.Cube[6]; Cube.Cube[7] = Cube.Cube[3];
                                Cube.Cube[6] = a; Cube.Cube[3] = b;

                                i++; continue;
                            }
                            break;

                        case 2:
                            a = Cube.Cube[38]; b = Cube.Cube[41]; c = Cube.Cube[44];
                            Cube.Cube[38] = Cube.Cube[29]; Cube.Cube[41] = Cube.Cube[32]; Cube.Cube[44] = Cube.Cube[35];
                            Cube.Cube[29] = Cube.Cube[47]; Cube.Cube[32] = Cube.Cube[50]; Cube.Cube[35] = Cube.Cube[53];
                            Cube.Cube[47] = Cube.Cube[15]; Cube.Cube[50] = Cube.Cube[12]; Cube.Cube[53] = Cube.Cube[9];
                            Cube.Cube[15] = a; Cube.Cube[12] = b; Cube.Cube[9] = c;

                            a = Cube.Cube[0]; b = Cube.Cube[1];
                            Cube.Cube[0] = Cube.Cube[6]; Cube.Cube[1] = Cube.Cube[3];
                            Cube.Cube[6] = Cube.Cube[8]; Cube.Cube[3] = Cube.Cube[7];
                            Cube.Cube[8] = Cube.Cube[2]; Cube.Cube[7] = Cube.Cube[5];
                            Cube.Cube[2] = a; Cube.Cube[5] = b;

                            a = Cube.Cube[38]; b = Cube.Cube[41]; c = Cube.Cube[44];
                            Cube.Cube[38] = Cube.Cube[29]; Cube.Cube[41] = Cube.Cube[32]; Cube.Cube[44] = Cube.Cube[35];
                            Cube.Cube[29] = Cube.Cube[47]; Cube.Cube[32] = Cube.Cube[50]; Cube.Cube[35] = Cube.Cube[53];
                            Cube.Cube[47] = Cube.Cube[15]; Cube.Cube[50] = Cube.Cube[12]; Cube.Cube[53] = Cube.Cube[9];
                            Cube.Cube[15] = a; Cube.Cube[12] = b; Cube.Cube[9] = c;

                            a = Cube.Cube[0]; b = Cube.Cube[1];
                            Cube.Cube[0] = Cube.Cube[6]; Cube.Cube[1] = Cube.Cube[3];
                            Cube.Cube[6] = Cube.Cube[8]; Cube.Cube[3] = Cube.Cube[7];
                            Cube.Cube[8] = Cube.Cube[2]; Cube.Cube[7] = Cube.Cube[5];
                            Cube.Cube[2] = a; Cube.Cube[5] = b;

                            i++; continue;
                    }
                    break;

                case 4:
                    switch (tim)
                    {
                        case 1:
                            if (dir == 1)
                            {
                                a = Cube.Cube[0]; b = Cube.Cube[3]; c = Cube.Cube[6];
                                Cube.Cube[0] = Cube.Cube[42]; Cube.Cube[3] = Cube.Cube[43]; Cube.Cube[6] = Cube.Cube[44];
                                Cube.Cube[42] = Cube.Cube[26]; Cube.Cube[43] = Cube.Cube[23]; Cube.Cube[44] = Cube.Cube[20];
                                Cube.Cube[26] = Cube.Cube[47]; Cube.Cube[23] = Cube.Cube[46]; Cube.Cube[20] = Cube.Cube[45];
                                Cube.Cube[47] = a; Cube.Cube[46] = b; Cube.Cube[45] = c;

                                a = Cube.Cube[27]; b = Cube.Cube[28];
                                Cube.Cube[27] = Cube.Cube[33]; Cube.Cube[28] = Cube.Cube[30];
                                Cube.Cube[33] = Cube.Cube[35]; Cube.Cube[30] = Cube.Cube[34];
                                Cube.Cube[35] = Cube.Cube[29]; Cube.Cube[34] = Cube.Cube[32];
                                Cube.Cube[29] = a; Cube.Cube[32] = b;

                                i++; continue;
                            }
                            else if (dir == -1)
                            {
                                a = Cube.Cube[6]; b = Cube.Cube[3]; c = Cube.Cube[0];
                                Cube.Cube[6] = Cube.Cube[45]; Cube.Cube[3] = Cube.Cube[46]; Cube.Cube[0] = Cube.Cube[47];
                                Cube.Cube[45] = Cube.Cube[20]; Cube.Cube[46] = Cube.Cube[23]; Cube.Cube[47] = Cube.Cube[26];
                                Cube.Cube[20] = Cube.Cube[44]; Cube.Cube[23] = Cube.Cube[43]; Cube.Cube[26] = Cube.Cube[42];
                                Cube.Cube[44] = a; Cube.Cube[43] = b; Cube.Cube[42] = c;

                                a = Cube.Cube[27]; b = Cube.Cube[28];
                                Cube.Cube[27] = Cube.Cube[29]; Cube.Cube[28] = Cube.Cube[32];
                                Cube.Cube[29] = Cube.Cube[35]; Cube.Cube[32] = Cube.Cube[34];
                                Cube.Cube[35] = Cube.Cube[33]; Cube.Cube[34] = Cube.Cube[30];
                                Cube.Cube[33] = a; Cube.Cube[30] = b;

                                i++; continue;
                            }
                            break;

                        case 2:
                            a = Cube.Cube[0]; b = Cube.Cube[3]; c = Cube.Cube[6];
                            Cube.Cube[0] = Cube.Cube[42]; Cube.Cube[3] = Cube.Cube[43]; Cube.Cube[6] = Cube.Cube[44];
                            Cube.Cube[42] = Cube.Cube[26]; Cube.Cube[43] = Cube.Cube[23]; Cube.Cube[44] = Cube.Cube[20];
                            Cube.Cube[26] = Cube.Cube[47]; Cube.Cube[23] = Cube.Cube[46]; Cube.Cube[20] = Cube.Cube[45];
                            Cube.Cube[47] = a; Cube.Cube[46] = b; Cube.Cube[45] = c;

                            a = Cube.Cube[27]; b = Cube.Cube[28];
                            Cube.Cube[27] = Cube.Cube[33]; Cube.Cube[28] = Cube.Cube[30];
                            Cube.Cube[33] = Cube.Cube[35]; Cube.Cube[30] = Cube.Cube[34];
                            Cube.Cube[35] = Cube.Cube[29]; Cube.Cube[34] = Cube.Cube[32];
                            Cube.Cube[29] = a; Cube.Cube[32] = b;

                            a = Cube.Cube[0]; b = Cube.Cube[3]; c = Cube.Cube[6];
                            Cube.Cube[0] = Cube.Cube[42]; Cube.Cube[3] = Cube.Cube[43]; Cube.Cube[6] = Cube.Cube[44];
                            Cube.Cube[42] = Cube.Cube[26]; Cube.Cube[43] = Cube.Cube[23]; Cube.Cube[44] = Cube.Cube[20];
                            Cube.Cube[26] = Cube.Cube[47]; Cube.Cube[23] = Cube.Cube[46]; Cube.Cube[20] = Cube.Cube[45];
                            Cube.Cube[47] = a; Cube.Cube[46] = b; Cube.Cube[45] = c;

                            a = Cube.Cube[27]; b = Cube.Cube[28];
                            Cube.Cube[27] = Cube.Cube[33]; Cube.Cube[28] = Cube.Cube[30];
                            Cube.Cube[33] = Cube.Cube[35]; Cube.Cube[30] = Cube.Cube[34];
                            Cube.Cube[35] = Cube.Cube[29]; Cube.Cube[34] = Cube.Cube[32];
                            Cube.Cube[29] = a; Cube.Cube[32] = b;

                            i++; continue;
                    }
                    break;

                case 5:
                    switch (tim)
                    {
                        case 1:
                            if (dir == 1)
                            {
                                a = Cube.Cube[36]; b = Cube.Cube[39]; c = Cube.Cube[42];
                                Cube.Cube[36] = Cube.Cube[17]; Cube.Cube[39] = Cube.Cube[14]; Cube.Cube[42] = Cube.Cube[11];
                                Cube.Cube[17] = Cube.Cube[45]; Cube.Cube[14] = Cube.Cube[48]; Cube.Cube[11] = Cube.Cube[51];
                                Cube.Cube[45] = Cube.Cube[27]; Cube.Cube[48] = Cube.Cube[30]; Cube.Cube[51] = Cube.Cube[33];
                                Cube.Cube[27] = a; Cube.Cube[30] = b; Cube.Cube[33] = c;

                                a = Cube.Cube[18]; b = Cube.Cube[19];
                                Cube.Cube[18] = Cube.Cube[24]; Cube.Cube[19] = Cube.Cube[21];
                                Cube.Cube[24] = Cube.Cube[26]; Cube.Cube[21] = Cube.Cube[25];
                                Cube.Cube[26] = Cube.Cube[20]; Cube.Cube[25] = Cube.Cube[23];
                                Cube.Cube[20] = a; Cube.Cube[23] = b;

                                i++; continue;
                            }
                            else if (dir == -1)
                            {
                                a = Cube.Cube[42]; b = Cube.Cube[39]; c = Cube.Cube[36];
                                Cube.Cube[42] = Cube.Cube[33]; Cube.Cube[39] = Cube.Cube[30]; Cube.Cube[36] = Cube.Cube[27];
                                Cube.Cube[33] = Cube.Cube[51]; Cube.Cube[30] = Cube.Cube[48]; Cube.Cube[27] = Cube.Cube[45];
                                Cube.Cube[51] = Cube.Cube[11]; Cube.Cube[48] = Cube.Cube[14]; Cube.Cube[45] = Cube.Cube[17];
                                Cube.Cube[11] = a; Cube.Cube[14] = b; Cube.Cube[17] = c;

                                b = Cube.Cube[19]; a = Cube.Cube[18];
                                Cube.Cube[19] = Cube.Cube[23]; Cube.Cube[18] = Cube.Cube[20];
                                Cube.Cube[23] = Cube.Cube[25]; Cube.Cube[20] = Cube.Cube[26];
                                Cube.Cube[25] = Cube.Cube[21]; Cube.Cube[26] = Cube.Cube[24];
                                Cube.Cube[21] = b; Cube.Cube[24] = a;

                                i++; continue;
                            }
                            break;

                        case 2:
                            a = Cube.Cube[36]; b = Cube.Cube[39]; c = Cube.Cube[42];
                            Cube.Cube[36] = Cube.Cube[17]; Cube.Cube[39] = Cube.Cube[14]; Cube.Cube[42] = Cube.Cube[11];
                            Cube.Cube[17] = Cube.Cube[45]; Cube.Cube[14] = Cube.Cube[48]; Cube.Cube[11] = Cube.Cube[51];
                            Cube.Cube[45] = Cube.Cube[27]; Cube.Cube[48] = Cube.Cube[30]; Cube.Cube[51] = Cube.Cube[33];
                            Cube.Cube[27] = a; Cube.Cube[30] = b; Cube.Cube[33] = c;

                            a = Cube.Cube[18]; b = Cube.Cube[19];
                            Cube.Cube[18] = Cube.Cube[24]; Cube.Cube[19] = Cube.Cube[21];
                            Cube.Cube[24] = Cube.Cube[26]; Cube.Cube[21] = Cube.Cube[25];
                            Cube.Cube[26] = Cube.Cube[20]; Cube.Cube[25] = Cube.Cube[23];
                            Cube.Cube[20] = a; Cube.Cube[23] = b;

                            a = Cube.Cube[36]; b = Cube.Cube[39]; c = Cube.Cube[42];
                            Cube.Cube[36] = Cube.Cube[17]; Cube.Cube[39] = Cube.Cube[14]; Cube.Cube[42] = Cube.Cube[11];
                            Cube.Cube[17] = Cube.Cube[45]; Cube.Cube[14] = Cube.Cube[48]; Cube.Cube[11] = Cube.Cube[51];
                            Cube.Cube[45] = Cube.Cube[27]; Cube.Cube[48] = Cube.Cube[30]; Cube.Cube[51] = Cube.Cube[33];
                            Cube.Cube[27] = a; Cube.Cube[30] = b; Cube.Cube[33] = c;

                            a = Cube.Cube[18]; b = Cube.Cube[19];
                            Cube.Cube[18] = Cube.Cube[24]; Cube.Cube[19] = Cube.Cube[21];
                            Cube.Cube[24] = Cube.Cube[26]; Cube.Cube[21] = Cube.Cube[25];
                            Cube.Cube[26] = Cube.Cube[20]; Cube.Cube[25] = Cube.Cube[23];
                            Cube.Cube[20] = a; Cube.Cube[23] = b;

                            i++; continue;
                    }
                    break;

                case 6:
                    switch (tim)
                    {
                        case 1:
                            if (dir == 1)
                            {
                                a = Cube.Cube[6]; b = Cube.Cube[7]; c = Cube.Cube[8];
                                Cube.Cube[6] = Cube.Cube[33]; Cube.Cube[7] = Cube.Cube[34]; Cube.Cube[8] = Cube.Cube[35];
                                Cube.Cube[33] = Cube.Cube[24]; Cube.Cube[34] = Cube.Cube[25]; Cube.Cube[35] = Cube.Cube[26];
                                Cube.Cube[24] = Cube.Cube[15]; Cube.Cube[25] = Cube.Cube[16]; Cube.Cube[26] = Cube.Cube[17];
                                Cube.Cube[15] = a; Cube.Cube[16] = b; Cube.Cube[17] = c;

                                a = Cube.Cube[47]; b = Cube.Cube[50];
                                Cube.Cube[47] = Cube.Cube[45]; Cube.Cube[50] = Cube.Cube[46];
                                Cube.Cube[45] = Cube.Cube[51]; Cube.Cube[46] = Cube.Cube[48];
                                Cube.Cube[51] = Cube.Cube[53]; Cube.Cube[48] = Cube.Cube[52];
                                Cube.Cube[53] = a; Cube.Cube[52] = b;

                                i++; continue;

                            }
                            else if (dir == -1)
                            {
                                a = Cube.Cube[8]; b = Cube.Cube[7]; c = Cube.Cube[6];
                                Cube.Cube[8] = Cube.Cube[17]; Cube.Cube[7] = Cube.Cube[16]; Cube.Cube[6] = Cube.Cube[15];
                                Cube.Cube[17] = Cube.Cube[26]; Cube.Cube[16] = Cube.Cube[25]; Cube.Cube[15] = Cube.Cube[24];
                                Cube.Cube[26] = Cube.Cube[35]; Cube.Cube[25] = Cube.Cube[34]; Cube.Cube[24] = Cube.Cube[33];
                                Cube.Cube[35] = a; Cube.Cube[34] = b; Cube.Cube[33] = c;

                                b = Cube.Cube[50]; a = Cube.Cube[47];
                                Cube.Cube[50] = Cube.Cube[52]; Cube.Cube[47] = Cube.Cube[53];
                                Cube.Cube[52] = Cube.Cube[48]; Cube.Cube[53] = Cube.Cube[51];
                                Cube.Cube[48] = Cube.Cube[46]; Cube.Cube[51] = Cube.Cube[45];
                                Cube.Cube[46] = b; Cube.Cube[45] = a;

                                i++; continue;
                            }
                            break;

                        case 2:
                            a = Cube.Cube[6]; b = Cube.Cube[7]; c = Cube.Cube[8];
                            Cube.Cube[6] = Cube.Cube[33]; Cube.Cube[7] = Cube.Cube[34]; Cube.Cube[8] = Cube.Cube[35];
                            Cube.Cube[33] = Cube.Cube[24]; Cube.Cube[34] = Cube.Cube[25]; Cube.Cube[35] = Cube.Cube[26];
                            Cube.Cube[24] = Cube.Cube[15]; Cube.Cube[25] = Cube.Cube[16]; Cube.Cube[26] = Cube.Cube[17];
                            Cube.Cube[15] = a; Cube.Cube[16] = b; Cube.Cube[17] = c;

                            a = Cube.Cube[47]; b = Cube.Cube[50];
                            Cube.Cube[47] = Cube.Cube[45]; Cube.Cube[50] = Cube.Cube[46];
                            Cube.Cube[45] = Cube.Cube[51]; Cube.Cube[46] = Cube.Cube[48];
                            Cube.Cube[51] = Cube.Cube[53]; Cube.Cube[48] = Cube.Cube[52];
                            Cube.Cube[53] = a; Cube.Cube[52] = b;

                            a = Cube.Cube[6]; b = Cube.Cube[7]; c = Cube.Cube[8];
                            Cube.Cube[6] = Cube.Cube[33]; Cube.Cube[7] = Cube.Cube[34]; Cube.Cube[8] = Cube.Cube[35];
                            Cube.Cube[33] = Cube.Cube[24]; Cube.Cube[34] = Cube.Cube[25]; Cube.Cube[35] = Cube.Cube[26];
                            Cube.Cube[24] = Cube.Cube[15]; Cube.Cube[25] = Cube.Cube[16]; Cube.Cube[26] = Cube.Cube[17];
                            Cube.Cube[15] = a; Cube.Cube[16] = b; Cube.Cube[17] = c;

                            a = Cube.Cube[47]; b = Cube.Cube[50];
                            Cube.Cube[47] = Cube.Cube[45]; Cube.Cube[50] = Cube.Cube[46];
                            Cube.Cube[45] = Cube.Cube[51]; Cube.Cube[46] = Cube.Cube[48];
                            Cube.Cube[51] = Cube.Cube[53]; Cube.Cube[48] = Cube.Cube[52];
                            Cube.Cube[53] = a; Cube.Cube[52] = b;

                            i++; continue;
                    }
                    break;
            }
            }
}
    

        public static void RotateCube(FridrichSolver cube, int stage, int caseIndex) {
            RotateCube(cube, stage, caseIndex, "");
        }

        public static void RotateCube(FridrichSolver cube, String algorithm) {
            RotateCube(cube, 123, 123, algorithm);
        }

        private static char[] concatChars(char[] left, String right) {
            StringBuilder out = new StringBuilder(left.length + right.length());
            out.append(left);
            out.append(right);
            return out.toString().toCharArray();
        }
}
