package br.com.fiapx.worker.application.exception;

import br.com.fiapx.contracts.ErrorCode;

public class ProcessingException extends RuntimeException {

    private final ErrorCode errorCode;

    public ProcessingException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ProcessingException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
