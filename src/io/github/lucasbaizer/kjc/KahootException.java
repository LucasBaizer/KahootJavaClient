package io.github.lucasbaizer.kjc;

public class KahootException extends Exception {
	private static final long serialVersionUID = -3846598801310911791L;

	public KahootException() {
		super();
	}

	public KahootException(Throwable e) {
		super(e);
	}

	public KahootException(String msg) {
		super(msg);
	}

	public KahootException(String msg, Throwable e) {
		super(msg, e);
	}
}
