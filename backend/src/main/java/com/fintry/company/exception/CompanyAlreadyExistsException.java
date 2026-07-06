package com.fintry.company.exception;

public class CompanyAlreadyExistsException extends RuntimeException {

    public CompanyAlreadyExistsException(String document) {
        super("Company with document " + document + " already exists.");
    }

}