using System;
using System.Collections.Generic;

namespace NeonTetris.Core
{
    public enum PieceType { I, O, T, S, Z, J, L }

    internal static class PieceData
    {
        // Koordinat (x,y) dalam kotak lokal, y ke bawah.
        private static readonly int[][] Base =
        {
            new[] { 0,1, 1,1, 2,1, 3,1 }, // I
            new[] { 0,0, 1,0, 0,1, 1,1 }, // O
            new[] { 1,0, 0,1, 1,1, 2,1 }, // T
            new[] { 1,0, 2,0, 0,1, 1,1 }, // S
            new[] { 0,0, 1,0, 1,1, 2,1 }, // Z
            new[] { 0,0, 0,1, 1,1, 2,1 }, // J
            new[] { 2,0, 0,1, 1,1, 2,1 }, // L
        };

        // SRS wall kick (sumbu y ke atas), indeks [rotasiAsal], 5 pasangan (dx,dy).
        private static readonly int[][] KickJlstzCw =
        {
            new[] { 0,0, -1,0, -1,1, 0,-2, -1,-2 },
            new[] { 0,0,  1,0,  1,-1, 0,2,  1,2 },
            new[] { 0,0,  1,0,  1,1, 0,-2,  1,-2 },
            new[] { 0,0, -1,0, -1,-1, 0,2, -1,2 },
        };
        private static readonly int[][] KickJlstzCcw =
        {
            new[] { 0,0,  1,0,  1,1, 0,-2,  1,-2 },
            new[] { 0,0,  1,0,  1,-1, 0,2,  1,2 },
            new[] { 0,0, -1,0, -1,1, 0,-2, -1,-2 },
            new[] { 0,0, -1,0, -1,-1, 0,2, -1,2 },
        };
        private static readonly int[][] KickICw =
        {
            new[] { 0,0, -2,0,  1,0, -2,-1,  1,2 },
            new[] { 0,0, -1,0,  2,0, -1,2,  2,-1 },
            new[] { 0,0,  2,0, -1,0,  2,1, -1,-2 },
            new[] { 0,0,  1,0, -2,0,  1,-2, -2,1 },
        };
        private static readonly int[][] KickICcw =
        {
            new[] { 0,0, -1,0,  2,0, -1,2,  2,-1 },
            new[] { 0,0,  2,0, -1,0,  2,1, -1,-2 },
            new[] { 0,0,  1,0, -2,0,  1,-2, -2,1 },
            new[] { 0,0, -2,0,  1,0, -2,-1,  1,2 },
        };
        private static readonly int[] NoKick = { 0, 0 };

        public static int BoxSize(PieceType t) => t == PieceType.I ? 4 : t == PieceType.O ? 2 : 3;

        public static (int x, int y)[] Cells(PieceType t, int rot)
        {
            int n = BoxSize(t);
            int[] b = Base[(int)t];
            var res = new (int, int)[4];
            for (int i = 0; i < 4; i++)
            {
                int x = b[i * 2], y = b[i * 2 + 1];
                for (int r = 0; r < rot; r++)
                {
                    int nx = n - 1 - y;
                    y = x;
                    x = nx;
                }
                res[i] = (x, y);
            }
            return res;
        }

        public static int[] Kicks(PieceType t, int fromRot, bool cw)
        {
            if (t == PieceType.O) return NoKick;
            if (t == PieceType.I) return (cw ? KickICw : KickICcw)[fromRot];
            return (cw ? KickJlstzCw : KickJlstzCcw)[fromRot];
        }
    }

    public sealed class TetrisGame
    {
        public const int Width = 10;
        public const int Height = 22;      // 20 terlihat + 2 baris tersembunyi
        public const int HiddenRows = 2;
        public const int ScorePerLevel = 2500;
        public const int MaxLevel = 20;

        private const float LockDelay = 0.5f;
        private const int MaxLockResets = 15;
        private const float MaxDeltaTime = 0.1f;
        private static readonly int[] LineScore = { 0, 100, 300, 500, 800 };

        private readonly Random _rng;
        private readonly Queue<PieceType> _queue = new Queue<PieceType>();
        private readonly int[,] _grid = new int[Width, Height];
        private float _gravityAcc;
        private float _lockTimer;
        private int _lockResets;
        private bool _canHold = true;

        public PieceType Current { get; private set; }
        public PieceType? Held { get; private set; }
        public int Rotation { get; private set; }
        public int X { get; private set; }
        public int Y { get; private set; }

        public long Score { get; private set; }
        public int Lines { get; private set; }
        public int Pieces { get; private set; }
        public float ElapsedSeconds { get; private set; }
        public bool IsGameOver { get; private set; }
        public int Seed { get; }
        public int Level => Math.Min(MaxLevel, 1 + (int)(Score / ScorePerLevel));

        public event Action<int> LinesCleared;
        public event Action PieceLocked;
        public event Action GameEnded;

        public TetrisGame(int seed)
        {
            Seed = seed;
            _rng = new Random(seed);
            for (int x = 0; x < Width; x++)
                for (int y = 0; y < Height; y++)
                    _grid[x, y] = -1;
            Spawn(NextPiece());
        }

        public int GetCell(int x, int y) => _grid[x, y];

        public IEnumerable<(int x, int y)> CurrentCells()
        {
            foreach (var c in PieceData.Cells(Current, Rotation))
                yield return (X + c.x, Y + c.y);
        }

        public int GhostY()
        {
            int gy = Y;
            while (Fits(Current, Rotation, X, gy + 1)) gy++;
            return gy;
        }

        public static (int x, int y)[] ShapeCells(PieceType type) => PieceData.Cells(type, 0);

        public IReadOnlyList<PieceType> PeekNext(int count)
        {
            EnsureBag(count);
            var list = new List<PieceType>(count);
            foreach (var p in _queue)
            {
                if (list.Count == count) break;
                list.Add(p);
            }
            return list;
        }

        public void Tick(float dt)
        {
            if (IsGameOver) return;
            dt = Math.Min(dt, MaxDeltaTime);
            ElapsedSeconds += dt;

            if (IsGrounded())
            {
                _gravityAcc = 0f;
                _lockTimer += dt;
                if (_lockTimer >= LockDelay) LockPiece();
                return;
            }

            _lockTimer = 0f;
            _gravityAcc += dt;
            float interval = GravityInterval(Level);
            while (_gravityAcc >= interval && Fits(Current, Rotation, X, Y + 1))
            {
                _gravityAcc -= interval;
                Y++;
            }
        }

        public bool MoveLeft() => TryShift(-1);
        public bool MoveRight() => TryShift(1);

        public bool SoftDrop()
        {
            if (IsGameOver || !Fits(Current, Rotation, X, Y + 1)) return false;
            Y++;
            Score += 1;
            return true;
        }

        public void HardDrop()
        {
            if (IsGameOver) return;
            int dist = GhostY() - Y;
            Y += dist;
            Score += 2L * dist;
            LockPiece();
        }

        public bool Rotate(bool clockwise)
        {
            if (IsGameOver) return false;
            int to = clockwise ? (Rotation + 1) % 4 : (Rotation + 3) % 4;
            int[] kicks = PieceData.Kicks(Current, Rotation, clockwise);
            for (int i = 0; i < kicks.Length; i += 2)
            {
                int nx = X + kicks[i];
                int ny = Y - kicks[i + 1]; // tabel kick y ke atas
                if (!Fits(Current, to, nx, ny)) continue;
                X = nx;
                Y = ny;
                Rotation = to;
                RegisterGroundedMove();
                return true;
            }
            return false;
        }

        public bool Hold()
        {
            if (IsGameOver || !_canHold) return false;
            PieceType incoming = Held ?? NextPiece();
            Held = Current;
            Spawn(incoming);
            _canHold = false;
            return true;
        }

        private bool TryShift(int dx)
        {
            if (IsGameOver || !Fits(Current, Rotation, X + dx, Y)) return false;
            X += dx;
            RegisterGroundedMove();
            return true;
        }

        private void RegisterGroundedMove()
        {
            if (IsGrounded() && _lockResets < MaxLockResets)
            {
                _lockResets++;
                _lockTimer = 0f;
            }
        }

        private bool IsGrounded() => !Fits(Current, Rotation, X, Y + 1);

        private bool Fits(PieceType t, int rot, int px, int py)
        {
            foreach (var c in PieceData.Cells(t, rot))
            {
                int cx = px + c.x, cy = py + c.y;
                if (cx < 0 || cx >= Width || cy >= Height) return false;
                if (cy >= 0 && _grid[cx, cy] >= 0) return false;
            }
            return true;
        }

        private void LockPiece()
        {
            foreach (var (cx, cy) in CurrentCells())
                if (cy >= 0) _grid[cx, cy] = (int)Current;

            Pieces++;
            int cleared = ClearFullRows();
            if (cleared > 0)
            {
                Score += (long)LineScore[cleared] * Level;
                Lines += cleared;
                LinesCleared?.Invoke(cleared);
            }

            PieceLocked?.Invoke();
            _canHold = true;
            Spawn(NextPiece());
        }

        private int ClearFullRows()
        {
            int cleared = 0;
            for (int y = Height - 1; y >= 0; y--)
            {
                if (!IsRowFull(y)) continue;
                for (int yy = y; yy > 0; yy--)
                    for (int x = 0; x < Width; x++)
                        _grid[x, yy] = _grid[x, yy - 1];
                for (int x = 0; x < Width; x++) _grid[x, 0] = -1;
                cleared++;
                y++; // cek ulang baris yang baru turun
            }
            return cleared;
        }

        private bool IsRowFull(int y)
        {
            for (int x = 0; x < Width; x++)
                if (_grid[x, y] < 0) return false;
            return true;
        }

        private void Spawn(PieceType t)
        {
            Current = t;
            Rotation = 0;
            X = (Width - PieceData.BoxSize(t)) / 2;
            Y = HiddenRows - 1; // spawn tepat di tepi atas area terlihat
            _gravityAcc = 0f;
            _lockTimer = 0f;
            _lockResets = 0;

            if (!Fits(t, 0, X, Y))
            {
                IsGameOver = true;
                GameEnded?.Invoke();
            }
        }

        private PieceType NextPiece()
        {
            EnsureBag(1);
            return _queue.Dequeue();
        }

        // 7-bag deterministik: seed yang sama menghasilkan urutan yang sama.
        private void EnsureBag(int minCount)
        {
            while (_queue.Count < Math.Max(minCount, 7))
            {
                var bag = new[] { PieceType.I, PieceType.O, PieceType.T, PieceType.S, PieceType.Z, PieceType.J, PieceType.L };
                for (int i = bag.Length - 1; i > 0; i--)
                {
                    int j = _rng.Next(i + 1);
                    (bag[i], bag[j]) = (bag[j], bag[i]);
                }
                foreach (var p in bag) _queue.Enqueue(p);
            }
        }

        private static float GravityInterval(int level) =>
            (float)Math.Pow(0.8 - (level - 1) * 0.007, level - 1);
    }
}
