package vn.laishop.common;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ApiErrors {
    private static final Logger LOG = LoggerFactory.getLogger(ApiErrors.class);
    public record FieldError(String field, String message) { }
    static ProblemDetail problem(int status, String code, String detail, HttpServletRequest request) {
        ProblemDetail result = ProblemDetail.forStatusAndDetail(org.springframework.http.HttpStatusCode.valueOf(status), detail);
        result.setProperty("code", code);
        result.setProperty("traceId", request.getAttribute("traceId"));
        return result;
    }
    @ExceptionHandler(ApiException.class)
    ProblemDetail application(ApiException error, HttpServletRequest request) { return problem(error.status(), error.code(), error.getMessage(), request); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException error, HttpServletRequest request) {
        ProblemDetail result = problem(400, "VALIDATION_ERROR", "Vui lòng kiểm tra thông tin đã nhập.", request);
        List<FieldError> fields = error.getBindingResult().getFieldErrors().stream().map(e -> new FieldError(e.getField(), e.getDefaultMessage())).toList();
        result.setProperty("fieldErrors", fields); return result;
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail malformed(HttpMessageNotReadableException error, HttpServletRequest request) { return problem(400, "INVALID_JSON", "Dữ liệu JSON không đúng định dạng hoặc kiểu dữ liệu.", request); }
    @ExceptionHandler({org.springframework.beans.TypeMismatchException.class,org.springframework.web.bind.MissingServletRequestParameterException.class,org.springframework.web.multipart.support.MissingServletRequestPartException.class})
    ProblemDetail invalidParameter(Exception error,HttpServletRequest request) { return problem(400,"INVALID_PARAMETER",error.getMessage(),request); }
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    ProblemDetail method(org.springframework.web.HttpRequestMethodNotSupportedException error,HttpServletRequest request) { return problem(405,"METHOD_NOT_ALLOWED","HTTP method không được hỗ trợ cho endpoint này.",request); }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail tooLarge(MaxUploadSizeExceededException error, HttpServletRequest request) { return problem(413, "FILE_TOO_LARGE", "File vượt quá giới hạn 10 MB.", request); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail constraint(DataIntegrityViolationException error, HttpServletRequest request) {
        LOG.atError().addKeyValue("traceId", request.getAttribute("traceId")).setCause(error).log("database_constraint_failed");
        return problem(409, "DATA_CONFLICT", "Dữ liệu xung đột với ràng buộc lưu trữ; hãy tải lại trước khi thử lại.", request);
    }
    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception error, HttpServletRequest request) {
        LOG.atError().addKeyValue("traceId", request.getAttribute("traceId")).setCause(error).log("request_failed");
        return problem(500, "INTERNAL_ERROR", "Không thể hoàn thành yêu cầu. Cung cấp mã traceId để kiểm tra lỗi.", request);
    }
}
