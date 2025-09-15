package com.example.tax.service.impl;

import com.core.lib.entity.Client;
import com.core.lib.entity.ClientContact;
import com.core.lib.entity.Country;
import com.core.lib.exception.BusinessException;
import com.core.lib.model.ClientContactDto;
import com.core.lib.model.ClientDto;
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
                    .orElseThrow(() -> new RuntimeException("Client not found with id " + clientId));

            updateClientFields(client,clientDto);

            Map<Long, ClientContact> existingContacts = client.getContacts().stream()
                    .collect(Collectors.toMap(ClientContact::getContactId, c -> c));

            List<ClientContact> updatedContacts = new ArrayList<>();

            if (clientDto.getContacts() != null) {
                for (ClientContactDto contactDto : clientDto.getContacts()) {
                    if (contactDto.getContactId() != null) {
                        ClientContact contact = existingContacts.get(contactDto.getContactId());
                        if (contact == null) {
                            throw new RuntimeException("Contact not found with id " + contactDto.getContactId());
                        }
                        contact.setContactType(contactDto.getContactType());
                        contact.setContactValue(contactDto.getContactValue());
                        contact.setIsPrimary(contactDto.getIsPrimary());
                        updatedContacts.add(contact);
                    } else {
                        ClientContact newContact = new ClientContact();
                        newContact.setContactType(contactDto.getContactType());
                        newContact.setContactValue(contactDto.getContactValue());
                        newContact.setIsPrimary(contactDto.getIsPrimary());
                        newContact.setClient(client);
                        updatedContacts.add(newContact);
                    }
                }
            }
            client.getContacts().clear();
            client.getContacts().addAll(updatedContacts);

            Client saved = clientRepository.save(client);
            return modelMapper.map(saved, ClientDto.class);

        } catch (BusinessException be) {
            log.error("BusinessException while updating client id={}: {}", clientId, be.getMessage());
            throw be;
        } catch (Exception e) {
            log.error("Unexpected error while updating client id={}", clientId, e);
            throw new RuntimeException("Failed to update client with id=" + clientId, e);
        }
    }

    private void updateClientFields(Client client, ClientDto dto) {
        client.setName(dto.getName());
        client.setEmail(dto.getEmail());
        client.setPhoneNumber(dto.getPhoneNumber());
        client.setAddress(dto.getAddress());
        client.setPanNumber(dto.getPanNumber());
        client.setPassportNumber(dto.getPassportNumber());
        client.setTaxResidencyCountry(dto.getTaxResidencyCountry());
        client.setKycStatus(dto.getKycStatus());
        client.setRiskProfile(dto.getRiskProfile());
        client.setPreferredCurrency(dto.getPreferredCurrency());
        client.setUpdatedBy(dto.getCreatedBy());
    }



    @Override
    public void delete(Long clientId) {
        log.info("Deleting client id={}", clientId);
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new BusinessException("400","Client not found with id " + clientId));
        clientRepository.delete(client);

    }
}
