package org.truegods.binary;

public class IntervalCronStatus extends CronStatus
{
	private final long interval;
	private final Long times;

	public IntervalCronStatus(final CronJob job, final long interval)
	{
		this(job, interval, null);
	}

	public IntervalCronStatus(final CronJob job, final long interval, final Long times)
	{
		super(job);
		this.interval = interval;
		this.times = times;
	}

	@Override public void reschedule()
	{
		if (times == null || runs < times)
			nextRun = System.currentTimeMillis() + interval;
		else
			nextRun = Long.MAX_VALUE;
	}

	@Override
	public boolean equals(final Object o)
	{
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		if (!super.equals(o)) return false;

		final IntervalCronStatus every = (IntervalCronStatus) o;

		return interval == every.interval &&
				!(times != null ? !times.equals(every.times) : every.times != null);
	}

	@Override
	public int hashCode()
	{
		int result = super.hashCode();
		result = 31 * result + (int) (interval ^ interval >>> 32);
		result = 31 * result + (times != null ? times.hashCode() : 0);
		return result;
	}
}

