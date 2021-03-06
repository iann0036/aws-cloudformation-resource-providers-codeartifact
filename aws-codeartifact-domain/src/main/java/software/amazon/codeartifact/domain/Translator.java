package software.amazon.codeartifact.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import software.amazon.awssdk.awscore.AwsRequest;
import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.codeartifact.model.AccessDeniedException;
import software.amazon.awssdk.services.codeartifact.model.CodeartifactException;
import software.amazon.awssdk.services.codeartifact.model.ConflictException;
import software.amazon.awssdk.services.codeartifact.model.CreateDomainRequest;
import software.amazon.awssdk.services.codeartifact.model.CreateDomainResponse;
import software.amazon.awssdk.services.codeartifact.model.DeleteDomainPermissionsPolicyRequest;
import software.amazon.awssdk.services.codeartifact.model.DeleteDomainRequest;
import software.amazon.awssdk.services.codeartifact.model.DescribeDomainRequest;
import software.amazon.awssdk.services.codeartifact.model.DescribeDomainResponse;
import software.amazon.awssdk.services.codeartifact.model.DomainDescription;
import software.amazon.awssdk.services.codeartifact.model.DomainSummary;
import software.amazon.awssdk.services.codeartifact.model.GetDomainPermissionsPolicyRequest;
import software.amazon.awssdk.services.codeartifact.model.InternalServerException;
import software.amazon.awssdk.services.codeartifact.model.ListDomainsRequest;
import software.amazon.awssdk.services.codeartifact.model.ListDomainsResponse;
import software.amazon.awssdk.services.codeartifact.model.PutDomainPermissionsPolicyRequest;
import software.amazon.awssdk.services.codeartifact.model.ResourceNotFoundException;
import software.amazon.awssdk.services.codeartifact.model.ServiceQuotaExceededException;
import software.amazon.awssdk.services.codeartifact.model.ValidationException;
import software.amazon.cloudformation.exceptions.CfnAccessDeniedException;
import software.amazon.cloudformation.exceptions.CfnAlreadyExistsException;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.exceptions.CfnResourceConflictException;
import software.amazon.cloudformation.exceptions.CfnServiceLimitExceededException;
import software.amazon.cloudformation.exceptions.TerminalException;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class is a centralized placeholder for
 *  - api request construction
 *  - object translation to/from aws sdk
 *  - resource model construction for read/list handlers
 */

public class Translator {
  public static final ObjectMapper MAPPER = new ObjectMapper();

  /**
   * Request to create a resource
   * @param model resource model
   * @return awsRequest the aws service request to create a resource
   */
  static CreateDomainRequest translateToCreateRequest(final ResourceModel model) {
    return CreateDomainRequest.builder()
        .domain(model.getDomainName())
        .build();
  }

  /**
   * Request to read a resource
   * @param model resource model
   * @param model resource handler request
   * @return awsRequest the aws service request to describe a resource
   */
  static DescribeDomainRequest translateToReadRequest(
      final ResourceModel model, final ResourceHandlerRequest<ResourceModel> request
  ) {
    String accountId = request.getAwsAccountId();
    return DescribeDomainRequest.builder()
        .domain(model.getDomainName())
        .domainOwner(accountId)
        .build();
  }

  /**
   * Request to read a resource
   * @param model resource model
   * @param model resource handler request
   * @return awsRequest the aws service request to describe a resource
   */
  static GetDomainPermissionsPolicyRequest translateToGetDomainPermissionPolicy(
      final ResourceModel model, final ResourceHandlerRequest<ResourceModel> request
  ) {
    String accountId = request.getAwsAccountId();
    return GetDomainPermissionsPolicyRequest.builder()
        .domain(model.getDomainName())
        .domainOwner(accountId)
        .build();
  }

  /**
   * Translates resource object from sdk into a resource model
   * @param awsResponse the aws service describe resource response
   * @return model resource model
   */
  static ResourceModel translateFromReadResponse(final DescribeDomainResponse awsResponse) {
    DomainDescription domain = awsResponse.domain();
    return ResourceModel.builder()
        .domainName(domain.name())
        .encryptionKey(domain.encryptionKey())
        .domainOwner(domain.owner())
        .assetSizeBytes(domain.assetSizeBytes().intValue())
        .createdTime(domain.createdTime().toString())
        .repositoryCount(domain.repositoryCount())
        .arn(domain.arn())
        .build();
  }

  /**
   * Request to delete a resource
   * @param model resource model
   * @return awsRequest the aws service request to delete a resource
   */
  static DeleteDomainRequest translateToDeleteRequest(final ResourceModel model) {
    return DeleteDomainRequest.builder()
        .domain(model.getDomainName())
        .domainOwner(model.getDomainOwner())
        .build();
  }

  /**
   * Request to update properties of a previously created resource
   * @param model resource model
   * @return awsRequest the aws service request to modify a resource
   */
  static PutDomainPermissionsPolicyRequest translatePutDomainPolicyRequest(final ResourceModel model) {
      try {
        return PutDomainPermissionsPolicyRequest.builder()
            .policyDocument(translatePolicyInput(model.getPolicyDocument()))
            .domainOwner(model.getDomainOwner())
            .domain(model.getDomainName())
            .build();
      } catch (final JsonProcessingException e) {
        throw new CfnInvalidRequestException(e);
      }
  }

  /**
   * Request to delete Domain permission policy
   * @param model resource model
   * @return awsRequest the aws service request to modify a resource
   */
  static DeleteDomainPermissionsPolicyRequest translateDeleteDomainPolicyRequest(final ResourceModel model) {
    return DeleteDomainPermissionsPolicyRequest.builder()
        .domainOwner(model.getDomainOwner())
        .domain(model.getDomainName())
        .build();
  }

  static String translatePolicyInput(final Object policy) throws JsonProcessingException {
    if (policy instanceof Map) {
      return MAPPER.writeValueAsString(policy);
    }
    return (String) policy;
  }

  /**
   * Request to list resources
   * @param nextToken token passed to the aws service list resources request
   * @return awsRequest the aws service request to list resources within aws account
   */
  static ListDomainsRequest translateToListRequest(final String nextToken) {
    return ListDomainsRequest.builder()
        .maxResults(Constants.MAX_ITEMS)
        .nextToken(nextToken)
        .build();
  }

  /**
   * Translates resource objects from sdk into a resource model (primary identifier only)
   * @param awsResponse the aws service describe resource response
   * @return list of resource models
   */
  static List<ResourceModel> translateFromListRequest(final ListDomainsResponse awsResponse) {
    return streamOfOrEmpty(awsResponse.domains())
        .map(domain -> ResourceModel.builder()
            // TODO change domainName to arn when CodeArtifactClient populates arn in the ListDomainsResponse
            .domainName(domain.name())
            .build())
        .collect(Collectors.toList());
  }

  private static <T> Stream<T> streamOfOrEmpty(final Collection<T> collection) {
    return Optional.ofNullable(collection)
        .map(Collection::stream)
        .orElseGet(Stream::empty);
  }


  static void throwCfnException(final AwsServiceException exception, String operation, String domainName) {
    if (exception instanceof AccessDeniedException)
      throw new CfnAccessDeniedException(operation, exception);
    if (exception instanceof ConflictException) {
        throw new CfnAlreadyExistsException(ResourceModel.TYPE_NAME, domainName, exception);
    }
    if (exception instanceof ResourceNotFoundException)
      throw new CfnNotFoundException(ResourceModel.TYPE_NAME, domainName, exception);
    if (exception instanceof ServiceQuotaExceededException)
      throw new CfnServiceLimitExceededException(ResourceModel.TYPE_NAME, exception.getMessage(), exception);
    if (exception instanceof ValidationException)
      throw new CfnInvalidRequestException(exception);
    if (exception instanceof InternalServerException)
      throw new CfnGeneralServiceException(exception);
    throw exception;
  }

}
