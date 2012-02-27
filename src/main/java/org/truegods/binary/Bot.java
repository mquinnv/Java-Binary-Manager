package org.truegods.binary;

public class Bot
{
	public String profile;
	public String server;
	public transient boolean running;
	public transient Double cpu;
	public transient Long ram;
	public transient long pid;

	public Bot()
	{
	}

	public Bot(final String profile, final String server)
	{
		this.profile = profile;
		this.server = server;
	}
}
