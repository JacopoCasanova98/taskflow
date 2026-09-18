package com.taskflow.shared.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

/** Targeted capture of TaskFlow events, independent of console timestamps and framework logs. */
public final class LogCapture implements AutoCloseable {
	private final Logger logger;
	private final Level previous;
	private final ListAppender<ILoggingEvent> appender = new ListAppender<>() {
		@Override
		protected void append(ILoggingEvent event) {
			event.prepareForDeferredProcessing();
			super.append(event);
		}
	};

	public LogCapture(Class<?> source) {
		logger = (Logger) LoggerFactory.getLogger(source);
		previous = logger.getLevel();
		logger.setLevel(Level.DEBUG);
		appender.start();
		logger.addAppender(appender);
	}

	public java.util.List<ILoggingEvent> events() { return appender.list; }

	public String messages() {
		return String.join("\n", events().stream().map(ILoggingEvent::getFormattedMessage).toList());
	}

	@Override
	public void close() {
		logger.detachAppender(appender);
		logger.setLevel(previous);
		appender.stop();
	}
}
