package vn.laishop.search;

/** Throw only for transient provider failures; invalid input must raise ApiException with status 400/422. */
public final class ProviderUnavailableException extends RuntimeException {
    public ProviderUnavailableException(String message,Throwable cause) { super(message,cause); }
}
