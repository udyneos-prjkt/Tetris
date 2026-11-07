using NeonTetris.Core;
using UnityEngine;

namespace NeonTetris.UnityLayer
{
    /// <summary>
    /// Gestur: geser horizontal = gerak, geser pelan ke bawah = soft drop,
    /// flick cepat ke bawah = hard drop, tap = rotasi (kanan CW, kiri CCW),
    /// swipe ke atas = hold.
    /// </summary>
    public sealed class TouchInputController : MonoBehaviour
    {
        [SerializeField] private float cellDragDp = 28f;
        [SerializeField] private float tapMaxSeconds = 0.2f;
        [SerializeField] private float tapMaxDp = 10f;
        [SerializeField] private float flickMinDpPerSecond = 1400f;
        [SerializeField] private float flickMinDistanceDp = 40f;
        [SerializeField] private float holdSwipeDp = 80f;

        private TetrisGame _game;
        private float _pxPerDp = 2f;
        private int _fingerId = -1;
        private Vector2 _start;
        private Vector2 _last;
        private float _startTime;
        private float _accX;
        private float _accY;
        private bool _moved;
        private bool _holdUsed;

        public void Bind(TetrisGame game) => _game = game;

        private void Awake()
        {
            if (Screen.dpi > 0f) _pxPerDp = Screen.dpi / 160f;
        }

        private void Update()
        {
            if (_game == null || _game.IsGameOver) return;

            foreach (Touch t in Input.touches)
            {
                if (_fingerId == -1 && t.phase == TouchPhase.Began)
                {
                    BeginTouch(t);
                    continue;
                }

                if (t.fingerId != _fingerId) continue;

                switch (t.phase)
                {
                    case TouchPhase.Moved:
                    case TouchPhase.Stationary:
                        DragTouch(t);
                        break;
                    case TouchPhase.Ended:
                        EndTouch(t);
                        _fingerId = -1;
                        break;
                    case TouchPhase.Canceled:
                        _fingerId = -1;
                        break;
                }
            }
        }

        private void BeginTouch(Touch t)
        {
            _fingerId = t.fingerId;
            _start = _last = t.position;
            _startTime = Time.unscaledTime;
            _accX = _accY = 0f;
            _moved = false;
            _holdUsed = false;
        }

        private void DragTouch(Touch t)
        {
            Vector2 delta = t.position - _last;
            _last = t.position;
            _accX += delta.x;
            _accY = Mathf.Max(0f, _accY - delta.y); // y layar ke atas; ke bawah = positif

            float cellPx = cellDragDp * _pxPerDp;

            while (_accX >= cellPx) { _game.MoveRight(); _accX -= cellPx; _moved = true; }
            while (_accX <= -cellPx) { _game.MoveLeft(); _accX += cellPx; _moved = true; }
            while (_accY >= cellPx) { _game.SoftDrop(); _accY -= cellPx; _moved = true; }

            if (!_holdUsed && t.position.y - _start.y >= holdSwipeDp * _pxPerDp)
            {
                _game.Hold();
                _holdUsed = true;
                _moved = true;
            }
        }

        private void EndTouch(Touch t)
        {
            float duration = Mathf.Max(Time.unscaledTime - _startTime, 0.001f);
            Vector2 total = t.position - _start;

            bool isTap = !_moved
                         && duration <= tapMaxSeconds
                         && total.magnitude <= tapMaxDp * _pxPerDp;
            if (isTap)
            {
                _game.Rotate(_start.x >= Screen.width * 0.5f);
                return;
            }

            float down = -total.y;
            bool isFlick = down >= flickMinDistanceDp * _pxPerDp
                           && down > Mathf.Abs(total.x)
                           && down / duration >= flickMinDpPerSecond * _pxPerDp;
            if (isFlick) _game.HardDrop();
        }
    }
}
