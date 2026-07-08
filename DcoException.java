package ru.inversion.utils.dco;

import ru.inversion.utils.IExceptionInfo;

/** */
public class DcoException extends RuntimeException implements IExceptionInfo {

    public DcoException( String message ) {
        super(message);
    }

    public DcoException( String message, Throwable cause ) {
        super(message, cause);
    }

    @Override
    public String getCategory() {
        return "Dco";
    }
}
