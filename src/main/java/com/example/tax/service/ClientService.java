package com.example.tax.service;

import com.core.lib.dto.onboarding.ClientDto;

public interface ClientService {

    ClientDto save(ClientDto clientDto);

    ClientDto getByClientId(Long clientId);

    ClientDto updateClient(Long clientId, ClientDto clientDto);

    void delete(Long clientId);
}
