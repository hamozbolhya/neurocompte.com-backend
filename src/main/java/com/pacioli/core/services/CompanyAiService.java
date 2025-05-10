package com.pacioli.core.services;

import com.pacioli.core.DTO.Company;

public interface CompanyAiService {

    /**
     * Creates a new company in the AI system
     * @param company The company to create
     * @return The created company with any server-generated fields
     */
    Company createCompany(Company company);

    /**
     * Updates an existing company in the AI system
     * @param companyId The ID of the company to update
     * @param company The company data to update
     * @return The updated company with any server-generated fields
     */
    Company updateCompany(Long companyId, Company company);

    /**
     * Deletes a company from the AI system
     * @param companyId The ID of the company to delete
     * @return true if deletion was successful, false otherwise
     */
    boolean deleteCompany(Long companyId);
}
