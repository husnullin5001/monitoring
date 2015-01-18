package monitoring.terminal.munic;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import monitoring.handler.Handler;
import monitoring.handler.HandlerStrategy;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ExtractMessagesRawData implements RawHandler {
	private static final Logger logger = LoggerFactory.getLogger(ExtractMessagesRawData.class);

	private List<Handler> handlers;
	private HandlerStrategy strategy;
	private MunicMessageFactory municMessageFactory;

	@Override
	public void procces(String message) {
		synchronized (queue) {
			queue.add(message);
			queue.notifyAll();
		}
	}

	private void proccesMessage(String message) {
		JSONTokener jsonTokener = new JSONTokener(message);
		JSONArray jsonArray = (JSONArray) jsonTokener.nextValue();
		for (int i = 0; i < jsonArray.length(); i++) {
			JSONObject jsonObject = jsonArray.getJSONObject(i);
			MunicMessage municMessage = municMessageFactory.createMessage(jsonObject);
			if (municMessage != null) {
				for (Handler handler : handlers) {
					handler.handle(municMessage, strategy);
				}
			}
		}
	}

	class RecieverMunicRawDataRunnable implements Runnable {
		@Override
		public void run() {
			while (processing) {
				try {
					String message = null;
					synchronized (queue) {
						if (queue.isEmpty()) {
							try {
								queue.wait();
							} catch (InterruptedException e) {
								break;
							}
						}
						message = queue.poll();
						queue.notifyAll();
					}

					if (message != null) {
						proccesMessage(message);
					}
				} catch (Exception e) {
					logger.error("ExtractMessagesRawData exception in RecieverMunicRawDataRunnable.", e);
				}
			}
		}
	}

	private Queue<String> queue = new ArrayDeque<String>();
	private Thread mainThread;
	private volatile boolean processing = true;

	public void startProcessing() {
		Runnable processRunnable = new RecieverMunicRawDataRunnable();
		mainThread = new Thread(processRunnable);
		mainThread.setName("ExtractMessagesRawData MAIN THREAD");
		mainThread.start();
	}

	public void stopProcessing() {
		processing = false;
		mainThread.interrupt();
		try {
			mainThread.join();
		} catch (InterruptedException e) {
		}
	}

	public void setHandlers(List<Handler> handlers) {
		this.handlers = handlers;
	}

	public void setStrategy(HandlerStrategy strategy) {
		this.strategy = strategy;
	}

	public void setMunicMessageFactory(MunicMessageFactory municMessageFactory) {
		this.municMessageFactory = municMessageFactory;
	}

}
