package com.upi.offline.controller;

import com.upi.offline.dto.ApiErrorResponse;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<ApiErrorResponse> handleError(HttpServletRequest request) {
        Object statusCode = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = statusCode != null ? (Integer) statusCode : HttpStatus.INTERNAL_SERVER_ERROR.value();

        Object errorMessage = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        String message = errorMessage != null ? errorMessage.toString() : "An error occurred";

        HttpStatus httpStatus = HttpStatus.resolve(status);
        String error = httpStatus != null ? httpStatus.getReasonPhrase() : "Error";

        ApiErrorResponse response = new ApiErrorResponse(status, error, message);
        response.setPath(request.getRequestURI());

        return ResponseEntity
                .status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }
}
