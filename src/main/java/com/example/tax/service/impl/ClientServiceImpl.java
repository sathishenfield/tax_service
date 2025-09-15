package com.example.tax.service.impl;

import com.core.lib.dto.onboarding.ClientContactDto;
import com.core.lib.dto.onboarding.ClientDto;
import com.core.lib.entity.Client;
import com.core.lib.entity.ClientContact;
import com.core.lib.entity.Country;
import com.core.lib.exception.BusinessException;
import com.example.tax.repository.ClientRepository;
import com.example.tax.repository.CountryRepository;
import com.example.tax.service.ClientService;
import jakarta.transaction.Transactional;
import lombok.extern.log4j.Log4j2;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Log4j2
public class ClientServiceImpl implements ClientService {

    private final ClientRepository clientRepository;
    private final CountryRepository countryRepository;
    private final ModelMapper modelMapper;

    public ClientServiceImpl(ClientRepository clientRepository, CountryRepository countryRepository, ModelMapper modelMapper) {
        this.clientRepository = clientRepository;
        this.countryRepository = countryRepository;
        this.modelMapper = modelMapper;
    }

    @Override
    @Transactional
    public ClientDto save(ClientDto clientDto) {
        log.info("Saving new client: {}", clientDto.getName());
        try {
            Client client = modelMapper.map(clientDto, Client.class);

            String countryCode = clientDto.getCountry() != null ? clientDto.getCountry().getCountryCode() : null;
            // set country details
            Country country = countryRepository .findByCountryCode(countryCode)
                    .orElseGet(() -> {
                        log.info("Country with code {} not found. Creating new country.", countryCode);
                        Country newCountry = modelMapper.map(clientDto.getCountry(), Country.class);
                        return countryRepository.save(newCountry);
                    });

            client.setCountry(country);

            // set contact details
            if (clientDto.getContacts() != null) {
                List<ClientContact> contacts = clientDto.getContacts().stream()
                        .map(contactDTO -> {
                            ClientContact contact = modelMapper.map(contactDTO, ClientContact.class);
                            contact.setClient(client);
                            return contact;
                        })
                        .collect(Collectors.toList());
                client.setContacts(contacts);
            }
            Client savedClient = clientRepository.save(client);
            log.info("Client saved successfully with  name: {}", savedClient.getName());

            return modelMapper.map(savedClient, ClientDto.class);
        } catch (Exception e) {
            log.error("Error occurred while saving client: {}", clientDto.getName(), e);
            throw new RuntimeException("Failed to save client: " + clientDto.getName(), e);
        }

    }

    @Override
    public ClientDto getByClientId(Long clientId) {
        log.info("Fetching client with id={}", clientId);
        if (clientId == null) {
            throw new IllegalArgumentException("Client ID must not be null");
        }
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new BusinessException("400","Client not found with id " + clientId));
        return modelMapper.map(client, ClientDto.class);

    }

    @Override
    @Transactional
    public ClientDto updateClient(Long clientId, ClientDto clientDto) {
        log.info("Updating client id={}", clientId);

        try {
            Client client = clientRepository.findById(clientId)
                    .orElseThrow(() -> new BusinessException("400","Client not found with id " + clientId));

            modelMapper.map(clientDto, client);

            // Update contacts (add / update / delete)
            if (clientDto.getContacts() != null) {
                Map<Long, ClientContact> existingContactsMap = client.getContacts().stream()
                        .collect(Collectors.toMap(ClientContact::getContactId, c -> c));

                List<ClientContact> finalContacts = new ArrayList<>();

                for (ClientContactDto contactDTO : clientDto.getContacts()) {
                    if (contactDTO.getContactId() != null && existingContactsMap.containsKey(contactDTO.getContactId())) {
                        // update existing
                        ClientContact existing = existingContactsMap.get(contactDTO.getContactId());
                        modelMapper.map(contactDTO, existing);
                        existing.setClient(client);
                        finalContacts.add(existing);
                        existingContactsMap.remove(contactDTO.getContactId());
                    } else {
                        // new contact
                        ClientContact newContact = modelMapper.map(contactDTO, ClientContact.class);
                        newContact.setClient(client);
                        finalContacts.add(newContact);
                    }
                }
                client.getContacts().clear();
                client.getContacts().addAll(finalContacts);
            }
            return modelMapper.map(clientRepository.save(client), ClientDto.class);
        } catch (Exception e) {
            log.error("BusinessException while updating client id={}: {}", clientId, e.getMessage());
            throw new RuntimeException("Failed to update client with id=" + clientId, e);
        }
    }

    @Override
    public void delete(Long clientId) {
        log.info("Deleting client id={}", clientId);
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new BusinessException("400","Client not found with id " + clientId));
        clientRepository.delete(client);

    }
}
