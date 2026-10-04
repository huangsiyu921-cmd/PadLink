using PadLink.Core;
using PadLink.Core.Protocol;

namespace PadLink.Core.Tests;

public sealed class InputTimeoutGuardTests
{
    private static readonly TimeSpan Timeout = ProtocolConstants.FailSafeTimeout;

    [Fact]
    public void BeforeAnyFrame_NeverReportsStale()
    {
        var time = new FakeTimeProvider();
        var guard = new InputTimeoutGuard(Timeout, time);

        time.Advance(TimeSpan.FromSeconds(5));

        Assert.False(guard.Poll());
        Assert.False(guard.IsStale);
    }

    [Fact]
    public void StayFresh_WhileFramesKeepArriving()
    {
        var time = new FakeTimeProvider();
        var guard = new InputTimeoutGuard(Timeout, time);

        for (var i = 0; i < 20; i++)
        {
            guard.OnFrame();
            time.Advance(TimeSpan.FromMilliseconds(16.6));
            Assert.False(guard.Poll());
        }

        Assert.False(guard.IsStale);
    }

    [Fact]
    public void ReportsTransitionExactlyOnce_AfterTimeout()
    {
        var time = new FakeTimeProvider();
        var guard = new InputTimeoutGuard(Timeout, time);

        guard.OnFrame();
        time.Advance(Timeout - TimeSpan.FromMilliseconds(1));
        Assert.False(guard.Poll(), "还没超时就该保持正常");

        time.Advance(TimeSpan.FromMilliseconds(2));
        Assert.True(guard.Poll(), "超时后必须报告一次跃迁，调用方据此归零");
        Assert.True(guard.IsStale);

        time.Advance(TimeSpan.FromSeconds(10));
        Assert.False(guard.Poll(), "跃迁只能报一次，不能每轮都归零");
    }

    [Fact]
    public void NewFrame_RecoversFromStale()
    {
        var time = new FakeTimeProvider();
        var guard = new InputTimeoutGuard(Timeout, time);

        guard.OnFrame();
        time.Advance(TimeSpan.FromSeconds(1));
        Assert.True(guard.Poll());

        guard.OnFrame();
        Assert.False(guard.IsStale);
        Assert.False(guard.Poll());
    }

    [Fact]
    public void Reset_ReturnsToInitialState()
    {
        var time = new FakeTimeProvider();
        var guard = new InputTimeoutGuard(Timeout, time);

        guard.OnFrame();
        time.Advance(TimeSpan.FromSeconds(1));
        Assert.True(guard.Poll());

        guard.Reset();
        time.Advance(TimeSpan.FromSeconds(1));

        Assert.False(guard.IsStale);
        Assert.False(guard.Poll());
    }

    [Fact]
    public void RejectsNonPositiveTimeout()
        => Assert.Throws<ArgumentOutOfRangeException>(() => new InputTimeoutGuard(TimeSpan.Zero));

    /// <summary>可控时钟：时间戳单位与 <see cref="TimeSpan.TicksPerSecond"/> 一致。</summary>
    private sealed class FakeTimeProvider : TimeProvider
    {
        private long _timestamp;

        public override long TimestampFrequency => TimeSpan.TicksPerSecond;

        public override long GetTimestamp() => _timestamp;

        public void Advance(TimeSpan delta) => _timestamp += delta.Ticks;
    }
}
