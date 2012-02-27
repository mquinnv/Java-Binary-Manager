package org.truegods.binary;

public class Log
{
	void info(final String msg, Object... args)
	{
		System.err.print("INFO: ");
		System.out.println(args == null || args.length == 0 ? msg : String.format(msg, args));
	}

	void error(final String msg, Object... args)
	{
		System.err.print("ERROR: ");
		System.err.println(args == null || args.length == 0 ? msg : String.format(msg, args));
	}

	void debug(final String msg, Object... args)
	{

	}

	void trace(final String msg, Object... args)
	{

	}

	boolean isDebugEnabled()
	{
		return false;
	}

	boolean isTraceEnabled()
	{
		return false;
	}

	private Log()
	{

	}

	private static Log $ = new Log();

	public static final Log getInstance(final Class owner)
	{
		return $;
	}

}
