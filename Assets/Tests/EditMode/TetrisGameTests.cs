using System.Linq;
using NeonTetris.Core;
using NUnit.Framework;

namespace NeonTetris.Tests
{
    public class TetrisGameTests
    {
        [Test]
        public void SameSeed_ProducesSamePieceSequence()
        {
            var a = new TetrisGame(42);
            var b = new TetrisGame(42);
            Assert.AreEqual(a.Current, b.Current);
            CollectionAssert.AreEqual(a.PeekNext(14).ToArray(), b.PeekNext(14).ToArray());
        }

        [Test]
        public void Bag_FirstSevenPiecesAreAllDistinct()
        {
            var g = new TetrisGame(7);
            var firstSeven = new[] { g.Current }.Concat(g.PeekNext(6)).Distinct().Count();
            Assert.AreEqual(7, firstSeven);
        }

        [Test]
        public void NewGame_StartsAtLevelOneWithZeroScore()
        {
            var g = new TetrisGame(1);
            Assert.AreEqual(1, g.Level);
            Assert.AreEqual(0, g.Score);
            Assert.IsFalse(g.IsGameOver);
        }

        [Test]
        public void HardDrop_LocksPieceAndAddsScore()
        {
            var g = new TetrisGame(1);
            g.HardDrop();
            Assert.AreEqual(1, g.Pieces);
            Assert.Greater(g.Score, 0);
        }

        [Test]
        public void MoveLeft_StopsAtWall()
        {
            var g = new TetrisGame(3);
            for (int i = 0; i < 20; i++) g.MoveLeft();
            Assert.IsFalse(g.MoveLeft());
            Assert.IsTrue(g.CurrentCells().All(c => c.x >= 0));
        }

        [Test]
        public void Hold_CanOnlyBeUsedOncePerPiece()
        {
            var g = new TetrisGame(5);
            Assert.IsTrue(g.Hold());
            Assert.IsFalse(g.Hold());
            g.HardDrop();
            Assert.IsTrue(g.Hold());
        }

        [Test]
        public void StackingInOnePlace_EndsGame()
        {
            var g = new TetrisGame(9);
            for (int i = 0; i < 100 && !g.IsGameOver; i++) g.HardDrop();
            Assert.IsTrue(g.IsGameOver);
        }

        [Test]
        public void Tick_AccumulatesElapsedTime_AndCapsLargeDelta()
        {
            var g = new TetrisGame(2);
            g.Tick(5f);
            Assert.LessOrEqual(g.ElapsedSeconds, 0.1f + 0.0001f);
        }
    }
}
