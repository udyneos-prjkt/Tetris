using System;
using NeonTetris.Core;
using UnityEngine;
using UnityEngine.EventSystems;
using UnityEngine.UI;

namespace NeonTetris.UnityLayer
{
    /// <summary>
    /// Membangun seluruh scene lewat kode (tanpa aset scene manual) dan menjalankan game loop.
    /// </summary>
    public sealed class GameBootstrap : MonoBehaviour
    {
        private const int VisibleRows = TetrisGame.Height - TetrisGame.HiddenRows;
        private const float Cell = 64f;
        private const float CellDraw = 59f;
        private const float BoardCenterY = -40f;

        private static readonly Color Background = new Color(0.03f, 0.04f, 0.09f, 1f);
        private static readonly Color EmptyCell = new Color(1f, 1f, 1f, 0.05f);
        private static readonly Color Accent = new Color(0f, 0.94f, 1f, 1f);
        private static readonly Color Muted = new Color(1f, 1f, 1f, 0.55f);
        private static readonly Color[] PieceColors =
        {
            new Color(0.00f, 0.94f, 1.00f), // I
            new Color(1.00f, 0.90f, 0.20f), // O
            new Color(0.72f, 0.32f, 1.00f), // T
            new Color(0.30f, 1.00f, 0.50f), // S
            new Color(1.00f, 0.25f, 0.35f), // Z
            new Color(0.25f, 0.50f, 1.00f), // J
            new Color(1.00f, 0.60f, 0.15f), // L
        };

        private TetrisGame _game;
        private TouchInputController _input;
        private Font _font;
        private Transform _canvas;
        private Image[,] _board;
        private MiniPiece _hold;
        private MiniPiece[] _next;
        private Text _scoreText;
        private Text _levelText;
        private Text _linesText;
        private GameObject _overPanel;
        private Text _overScore;

        private long _shownScore = -1;
        private int _shownLevel = -1;
        private int _shownLines = -1;
        private int _shownPieces = -1;
        private PieceType? _shownHeld;

        [RuntimeInitializeOnLoadMethod(RuntimeInitializeLoadType.AfterSceneLoad)]
        private static void Init()
        {
            if (FindObjectOfType<GameBootstrap>() != null) return;
            new GameObject("NeonTetris").AddComponent<GameBootstrap>();
        }

        private void Awake()
        {
            Application.targetFrameRate = 60;
            Screen.sleepTimeout = SleepTimeout.NeverSleep;
            Screen.orientation = ScreenOrientation.Portrait;

            _font = Resources.GetBuiltinResource<Font>("LegacyRuntime.ttf");
            BuildCamera();
            BuildUi();
            _input = gameObject.AddComponent<TouchInputController>();
            StartGame();
        }

        private void Update()
        {
            if (Input.GetKeyDown(KeyCode.Escape))
            {
                Application.Quit();
                return;
            }

            if (_game == null) return;
            _game.Tick(Time.deltaTime);
            Render();
        }

        private void StartGame()
        {
            _game = new TetrisGame(Environment.TickCount);
            _game.GameEnded += OnGameEnded;
            _input.Bind(_game);
            _overPanel.SetActive(false);
            _shownScore = _shownLevel = _shownLines = _shownPieces = -1;
            _shownHeld = null;
        }

        private void OnGameEnded()
        {
            _overScore.text = "SKOR  " + _game.Score.ToString("N0");
            _overPanel.SetActive(true);
        }

        // ---------- Render ----------

        private void Render()
        {
            for (int x = 0; x < TetrisGame.Width; x++)
            {
                for (int row = 0; row < VisibleRows; row++)
                {
                    int v = _game.GetCell(x, row + TetrisGame.HiddenRows);
                    _board[x, row].color = v >= 0 ? PieceColors[v] : EmptyCell;
                }
            }

            if (!_game.IsGameOver)
            {
                Color c = PieceColors[(int)_game.Current];
                int ghostOffset = _game.GhostY() - _game.Y;
                Color ghost = new Color(c.r, c.g, c.b, 0.25f);
                foreach (var (cx, cy) in _game.CurrentCells()) Paint(cx, cy + ghostOffset, ghost);
                foreach (var (cx, cy) in _game.CurrentCells()) Paint(cx, cy, c);
            }

            if (_game.Score != _shownScore)
            {
                _shownScore = _game.Score;
                _scoreText.text = _shownScore.ToString("N0");
            }
            if (_game.Level != _shownLevel)
            {
                _shownLevel = _game.Level;
                _levelText.text = "LEVEL " + _shownLevel;
            }
            if (_game.Lines != _shownLines)
            {
                _shownLines = _game.Lines;
                _linesText.text = "LINES " + _shownLines;
            }
            if (_game.Pieces != _shownPieces || _game.Held != _shownHeld)
            {
                _shownPieces = _game.Pieces;
                _shownHeld = _game.Held;
                _hold.Show(_game.Held);
                var upcoming = _game.PeekNext(_next.Length);
                for (int i = 0; i < _next.Length; i++) _next[i].Show(upcoming[i]);
            }
        }

        private void Paint(int x, int y, Color color)
        {
            int row = y - TetrisGame.HiddenRows;
            if (x < 0 || x >= TetrisGame.Width || row < 0 || row >= VisibleRows) return;
            _board[x, row].color = color;
        }

        // ---------- Build UI ----------

        private void BuildCamera()
        {
            var go = new GameObject("Camera");
            go.transform.SetParent(transform, false);
            var cam = go.AddComponent<Camera>();
            cam.clearFlags = CameraClearFlags.SolidColor;
            cam.backgroundColor = Background;
            cam.orthographic = true;
            cam.cullingMask = 0;
        }

        private void BuildUi()
        {
            if (FindObjectOfType<EventSystem>() == null)
                new GameObject("EventSystem", typeof(EventSystem), typeof(StandaloneInputModule));

            var canvasGo = new GameObject("Canvas", typeof(RectTransform), typeof(Canvas), typeof(CanvasScaler), typeof(GraphicRaycaster));
            canvasGo.transform.SetParent(transform, false);
            canvasGo.GetComponent<Canvas>().renderMode = RenderMode.ScreenSpaceOverlay;
            var scaler = canvasGo.GetComponent<CanvasScaler>();
            scaler.uiScaleMode = CanvasScaler.ScaleMode.ScaleWithScreenSize;
            scaler.referenceResolution = new Vector2(1080f, 1920f);
            scaler.screenMatchMode = CanvasScaler.ScreenMatchMode.MatchWidthOrHeight;
            scaler.matchWidthOrHeight = 0.5f;
            _canvas = canvasGo.transform;

            MakeText("Title", _canvas, new Vector2(0f, 905f), new Vector2(900f, 50f),
                "NEON TETRIS  v1.1 beta", 30, Muted, TextAnchor.MiddleCenter);

            // Papan
            float boardW = TetrisGame.Width * Cell;
            float boardH = VisibleRows * Cell;
            MakeImage("Frame", _canvas, new Vector2(0f, BoardCenterY), new Vector2(boardW + 16f, boardH + 16f),
                new Color(Accent.r, Accent.g, Accent.b, 0.35f));
            MakeImage("Well", _canvas, new Vector2(0f, BoardCenterY), new Vector2(boardW + 8f, boardH + 8f),
                new Color(0.02f, 0.03f, 0.07f, 1f));

            _board = new Image[TetrisGame.Width, VisibleRows];
            for (int x = 0; x < TetrisGame.Width; x++)
            {
                for (int row = 0; row < VisibleRows; row++)
                {
                    float px = (x - (TetrisGame.Width - 1) / 2f) * Cell;
                    float py = BoardCenterY + ((VisibleRows - 1) / 2f - row) * Cell;
                    _board[x, row] = MakeImage("c", _canvas, new Vector2(px, py), new Vector2(CellDraw, CellDraw), EmptyCell);
                }
            }

            // HUD atas
            MakeText("HoldLabel", _canvas, new Vector2(-420f, 835f), new Vector2(200f, 40f), "HOLD", 28, Muted, TextAnchor.MiddleCenter);
            _hold = new MiniPiece(_canvas, new Vector2(-420f, 780f), PieceColors);

            MakeText("ScoreLabel", _canvas, new Vector2(-120f, 850f), new Vector2(400f, 40f), "SKOR", 28, Muted, TextAnchor.MiddleCenter);
            _scoreText = MakeText("Score", _canvas, new Vector2(-120f, 785f), new Vector2(420f, 100f), "0", 76, Color.white, TextAnchor.MiddleCenter);
            _scoreText.fontStyle = FontStyle.Bold;

            MakeText("NextLabel", _canvas, new Vector2(300f, 835f), new Vector2(400f, 40f), "NEXT", 28, Muted, TextAnchor.MiddleCenter);
            _next = new MiniPiece[3];
            for (int i = 0; i < _next.Length; i++)
                _next[i] = new MiniPiece(_canvas, new Vector2(170f + i * 130f, 780f), PieceColors);

            // HUD bawah
            _levelText = MakeText("Level", _canvas, new Vector2(-270f, -760f), new Vector2(440f, 70f), "LEVEL 1", 46, Accent, TextAnchor.MiddleCenter);
            _linesText = MakeText("Lines", _canvas, new Vector2(270f, -760f), new Vector2(440f, 70f), "LINES 0", 46, Color.white, TextAnchor.MiddleCenter);
            MakeText("Hint", _canvas, new Vector2(0f, -860f), new Vector2(1040f, 50f),
                "Geser: gerak  |  Tap: putar  |  Flick bawah: drop  |  Swipe atas: hold", 24, Muted, TextAnchor.MiddleCenter);

            BuildGameOverPanel();
        }

        private void BuildGameOverPanel()
        {
            var overlay = MakeImage("GameOver", _canvas, Vector2.zero, new Vector2(4000f, 4000f), new Color(0f, 0f, 0f, 0.85f));
            overlay.raycastTarget = true;
            _overPanel = overlay.gameObject;

            MakeText("OverTitle", overlay.transform, new Vector2(0f, 220f), new Vector2(900f, 140f),
                "GAME OVER", 100, new Color(1f, 0.25f, 0.35f), TextAnchor.MiddleCenter).fontStyle = FontStyle.Bold;
            _overScore = MakeText("OverScore", overlay.transform, new Vector2(0f, 80f), new Vector2(900f, 100f),
                "SKOR  0", 64, Color.white, TextAnchor.MiddleCenter);

            var button = MakeImage("Retry", overlay.transform, new Vector2(0f, -120f), new Vector2(520f, 140f), Accent);
            button.raycastTarget = true;
            button.gameObject.AddComponent<Button>().onClick.AddListener(StartGame);
            MakeText("RetryLabel", button.transform, Vector2.zero, new Vector2(520f, 140f),
                "MAIN LAGI", 54, Background, TextAnchor.MiddleCenter).fontStyle = FontStyle.Bold;
        }

        private static RectTransform MakeRect(string name, Transform parent, Vector2 pos, Vector2 size)
        {
            var go = new GameObject(name, typeof(RectTransform));
            var rt = (RectTransform)go.transform;
            rt.SetParent(parent, false);
            rt.anchorMin = rt.anchorMax = rt.pivot = new Vector2(0.5f, 0.5f);
            rt.anchoredPosition = pos;
            rt.sizeDelta = size;
            return rt;
        }

        private static Image MakeImage(string name, Transform parent, Vector2 pos, Vector2 size, Color color)
        {
            var img = MakeRect(name, parent, pos, size).gameObject.AddComponent<Image>();
            img.color = color;
            img.raycastTarget = false;
            return img;
        }

        private Text MakeText(string name, Transform parent, Vector2 pos, Vector2 size, string value,
            int fontSize, Color color, TextAnchor anchor)
        {
            var t = MakeRect(name, parent, pos, size).gameObject.AddComponent<Text>();
            t.font = _font;
            t.text = value;
            t.fontSize = fontSize;
            t.color = color;
            t.alignment = anchor;
            t.horizontalOverflow = HorizontalWrapMode.Overflow;
            t.verticalOverflow = VerticalWrapMode.Overflow;
            t.raycastTarget = false;
            return t;
        }

        private sealed class MiniPiece
        {
            private const float Step = 30f;
            private readonly Image[] _blocks = new Image[4];
            private readonly Vector2 _center;
            private readonly Color[] _colors;

            public MiniPiece(Transform parent, Vector2 center, Color[] colors)
            {
                _center = center;
                _colors = colors;
                MakeImage("MiniBg", parent, center, new Vector2(124f, 74f), new Color(1f, 1f, 1f, 0.06f));
                for (int i = 0; i < _blocks.Length; i++)
                    _blocks[i] = MakeImage("b", parent, center, new Vector2(Step - 4f, Step - 4f), Color.white);
            }

            public void Show(PieceType? type)
            {
                foreach (var b in _blocks) b.gameObject.SetActive(type.HasValue);
                if (!type.HasValue) return;

                var cells = TetrisGame.ShapeCells(type.Value);
                int minX = int.MaxValue, maxX = int.MinValue, minY = int.MaxValue, maxY = int.MinValue;
                foreach (var c in cells)
                {
                    minX = Math.Min(minX, c.x); maxX = Math.Max(maxX, c.x);
                    minY = Math.Min(minY, c.y); maxY = Math.Max(maxY, c.y);
                }

                float midX = (minX + maxX) / 2f;
                float midY = (minY + maxY) / 2f;
                for (int i = 0; i < _blocks.Length; i++)
                {
                    _blocks[i].color = _colors[(int)type.Value];
                    _blocks[i].rectTransform.anchoredPosition =
                        _center + new Vector2((cells[i].x - midX) * Step, -(cells[i].y - midY) * Step);
                }
            }
        }
    }
}
