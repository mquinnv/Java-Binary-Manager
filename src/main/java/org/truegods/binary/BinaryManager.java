package org.truegods.binary;

import org.hyperic.sigar.Sigar;
import org.hyperic.sigar.SigarException;
import org.hyperic.sigar.ptql.ProcessQueryFactory;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

import static javax.swing.JFileChooser.*;
import static javax.swing.JOptionPane.*;

public class BinaryManager extends JFrame
{
	private JPanel contentPane;
	private JTable bots;
	private JTextField profile;
	private JButton add;
	private JTextField server;
	private JButton stop;
	private JButton start;
	private JButton restart;
	private JButton delete;
	private JLabel yaeb;
	private JButton change;
	private JCheckBox auto;
	private JButton show;
	private BotModel model;
	private final Sigar sigar;

	public BinaryManager()
	{
		setContentPane(contentPane);

		setDefaultCloseOperation(EXIT_ON_CLOSE);
		model = new BotModel();
		bots.setModel(model);
		model.addTableModelListener(new TableModelListener()
		{
			public void tableChanged(final TableModelEvent event)
			{
				enableButtons();
			}
		});
		bots.getSelectionModel().addListSelectionListener(new ListSelectionListener()
		{
			public void valueChanged(final ListSelectionEvent event)
			{
				enableButtons();
			}
		});

		add.addActionListener(new ActionListener()
		{
			public void actionPerformed(final ActionEvent event)
			{
				onAdd();
			}
		});
		delete.addActionListener(new ActionListener()
		{
			public void actionPerformed(final ActionEvent event)
			{
				onDelete();
			}
		});
		change.addActionListener(new ActionListener()
		{
			public void actionPerformed(final ActionEvent event)
			{
				onChange();
			}
		});
		start.addActionListener(new ActionListener()
		{
			public void actionPerformed(final ActionEvent event)
			{
				onStart();
			}
		});
		stop.addActionListener(new ActionListener()
		{
			public void actionPerformed(final ActionEvent event)
			{
				onStop();
			}
		});
		restart.addActionListener(new ActionListener()
		{
			public void actionPerformed(final ActionEvent event)
			{
				onRestart();
			}
		});
		show.addActionListener(new ActionListener()
		{
			public void actionPerformed(final ActionEvent event)
			{
				onShow();
			}
		});

		sigar = new Sigar();
		if (SystemTray.isSupported())
		{

			final SystemTray tray = SystemTray.getSystemTray();
			final Image image = Toolkit.getDefaultToolkit().getImage(BinaryManager.class.getResource("/evony.gif"));

			final TrayIcon trayIcon = new TrayIcon(image, "Telescope");
			trayIcon.setImageAutoSize(true);
			trayIcon.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(final MouseEvent mouseEvent)
				{
					BinaryManager.this.setVisible(!BinaryManager.this.isVisible());
				}

			});

			try
			{
				tray.add(trayIcon);
			}
			catch (AWTException e)
			{
				throw new RuntimeException(e);
			}
		}
	}

	private void enableButtons()
	{
		final int[] rows = bots.getSelectedRows();
		boolean hasRunning = false;
		boolean hasNotRunning = false;
		for (int row : rows)
		{
			final Bot bot = model.bots.get(row);
			hasRunning |= bot.running;
			hasNotRunning |= !bot.running;
		}
		start.setEnabled(hasNotRunning);
		restart.setEnabled(hasRunning);
		stop.setEnabled(hasRunning);
		show.setEnabled(rows.length == 1 && model.bots.get(rows[0]).running);
		delete.setEnabled(rows.length > 0);
	}

	public static void main(String[] args)
	{
		BinaryManager dialog = new BinaryManager();
		dialog.pack();
		dialog.setSize(600, 350);
		dialog.init();
		dialog.setLocationRelativeTo(null);
		dialog.setVisible(true);
	}

	private void init()
	{
		loadConfig();
		auto.addChangeListener(new ChangeListener()
		{
			public void stateChanged(final ChangeEvent event)
			{
				saveConfig();
			}
		});
		System.out.println("querying sigar");
		Cron.interval(5, TimeUnit.SECONDS, new CronJob()
		{
			public String getName()
			{
				return "Sigar Update";
			}

			public void exec(final CronStatus status) throws Throwable
			{
				final long[] pids = ProcessQueryFactory.getInstance().getQuery("Exe.Name.ew=mdm_flash_player").find(sigar);
				final Set<Long> running = new HashSet<Long>(pids.length);
				for (long pid : pids)
				{
					running.add(pid);
					final String[] args = sigar.getProcArgs(pid);
					final double cpu = sigar.getProcCpu(pid).getPercent();
					final long mem = sigar.getProcMem(pid).getResident();
					boolean foundProfile = false;
					boolean foundServer = false;
					String profile = "";
					String server = null;

					for (String arg : args)
					{
						if (foundProfile)
						{
							profile = arg;
							foundProfile = false;
						}
						else if (foundServer)
						{
							server = arg;
							foundServer = false;
						}
						else if ("-profile".equals(arg))
							foundProfile = true;
						else if ("-server".equals(arg))
							foundServer = true;

					}
					if (profile != null && server != null)
					{
						for (Bot bot : model.bots)
						{
							if (server.equals(bot.server) && profile.equals(bot.profile))
							{
								bot.cpu = cpu;
								bot.ram = mem;
								bot.running = true;
								bot.pid = pid;
								model.update(bot);
								break;
							}
						}
					}
				}
				final Set<Bot> closed = new HashSet<Bot>(0);
				for (final Bot bot : model.bots)
				{
					if (bot.running && !running.contains(bot.pid))
					{
						bot.running = false;
						bot.pid = 0;
						bot.cpu = null;
						bot.ram = null;
						model.update(bot);
						closed.add(bot);
					}
				}
				if (auto.isSelected())
				{
					int delay = 0;
					for (Bot bot : closed)
					{
						start(delay, bot);
						delay += 1;
					}
				}

			}
		});
	}

	private void loadConfig()
	{
		final Preferences preferences = Preferences.userNodeForPackage(BinaryManager.class);
		yaeb.setText(preferences.get("yaeb", "none"));
		auto.setSelected(preferences.getBoolean("auto", true));
		int numBots = preferences.getInt("numBots", 0);
		for (int i = 0; i < numBots; i++)
		{
			final String profile = preferences.get("bot" + i + ".profile", "");
			final String server = preferences.get("bot" + i + ".server", "");
			model.add(new Bot(profile, server));
		}
	}

	private void saveConfig()
	{
		final Preferences preferences = Preferences.userNodeForPackage(BinaryManager.class);
		preferences.put("yaeb", yaeb.getText());
		preferences.putBoolean("auto", auto.isSelected());
		preferences.putInt("numBots", model.bots.size());
		int i = 0;
		for (Bot bot : model.bots)
		{
			preferences.put("bot" + i + ".profile", bot.profile);
			preferences.put("bot" + i + ".server", bot.server);
			i++;
		}
	}

	private void onAdd()
	{
		model.add(new Bot(profile.getText(), server.getText()));
		saveConfig();
	}

	private void onDelete()
	{
		final int[] rows = bots.getSelectedRows();
		for (int i = rows.length - 1; i >= 0; i--)
			model.remove(rows[i]);
		saveConfig();
	}

	private void onChange()
	{
		JFileChooser chooser = new JFileChooser();
		FileNameExtensionFilter filter = new FileNameExtensionFilter("Applications", "exe", "app");
		chooser.setFileFilter(filter);
		if (chooser.showOpenDialog(this) == APPROVE_OPTION)
		{
			yaeb.setText(chooser.getSelectedFile().getAbsolutePath());
			saveConfig();
		}
	}

	private void onStart()
	{
		int delay = 0;
		for (int row : bots.getSelectedRows())
		{
			final Bot bot = model.bots.get(row);
			if (!bot.running)
			{
				start(delay, bot);
				delay += 1;
			}
		}
	}

	private void start(int delay, final Bot bot)
	{
		final File yaeb = new File(this.yaeb.getText());
		if(!yaeb.exists())
		{
			showMessageDialog(this, String.format("%s is not present",yaeb.getAbsolutePath()));
			return;
		}
		final ProcessBuilder builder = new ProcessBuilder();
		builder.directory(yaeb.getParentFile());
		final File profileApp = new File(yaeb.getParentFile(), String.format("%s %s.app",bot.profile,bot.server));
		if (yaeb.renameTo(profileApp))
		{
			builder.command("open", "-n", profileApp.getName(), "--args", "-autostart", "-autorun", "-profile", bot.profile,
					"-server", bot.server,
					"-delay", Integer.toString(delay));
		}
		try
		{
			final Process proc = builder.start();
			proc.waitFor();
			Thread.sleep(20000);
			if (!profileApp.renameTo(yaeb))
				System.err.println("couldn't name back to original");
			delay += 1;
		}
		catch (IOException e)
		{
			showMessageDialog(this, e.getMessage());
		}
		catch (InterruptedException e)
		{
			showMessageDialog(this, e.getMessage());
		}
	}

	private void onStop()
	{
		for (int row : bots.getSelectedRows())
			kill(model.bots.get(row));
	}

	private void kill(final Bot bot)
	{
		try
		{
			sigar.kill(bot.pid, 1);
			bot.pid = 0;
			bot.running = false;
			bot.cpu = null;
			bot.ram = null;
			model.update(bot);
		}
		catch (SigarException e)
		{
			showMessageDialog(this, e.getMessage());
		}
	}

	private void onRestart()
	{
		final Set<Bot> toRestart = new HashSet<Bot>(0);
		for (int row : bots.getSelectedRows())
		{
			final Bot bot = model.bots.get(row);
			if (bot.running)
			{
				toRestart.add(bot);
				kill(bot);
			}
		}
		int delay = 0;
		for (final Bot bot : toRestart)
		{
			start(delay, bot);
			delay += 1;
		}
	}

	private void onShow()
	{
		final String script = String.format(focusScript, model.bots.get(bots.getSelectedRow()).pid);
		final ProcessBuilder builder = new ProcessBuilder();
		builder.command("osascript", "-e", script);
		try
		{
			builder.start();
		}
		catch (IOException e)
		{
			showMessageDialog(this, e.getMessage());
		}
	}

	private static final String focusScript =
			"tell application \"System Events\"\n" +
			"    set theprocs to every process whose unix id is %d\n" +
			"    repeat with proc in theprocs\n" +
			"        set the frontmost of proc to true\n" +
			"    end repeat\n" +
			"end tell";

}
