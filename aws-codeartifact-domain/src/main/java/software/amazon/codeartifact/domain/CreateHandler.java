package software.amazon.codeartifact.domain;

import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.codeartifact.CodeartifactClient;
import software.amazon.awssdk.services.codeartifact.model.CreateDomainRequest;
import software.amazon.awssdk.services.codeartifact.model.CreateDomainResponse;
import software.amazon.awssdk.services.codeartifact.model.DescribeDomainResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;


public class CreateHandler extends BaseHandlerStd {
    private Logger logger;

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<CodeartifactClient> proxyClient,
        final Logger logger) {

        this.logger = logger;
        return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
            .then(progress -> createDomain(proxy, progress, request, proxyClient))
            .then(progress -> putDomainPermissionsPolicy(proxy, progress, callbackContext, request, proxyClient, logger))
            .then(progress -> new ReadHandler().handleRequest(proxy, request, callbackContext, proxyClient, logger));
    }

    private ProgressEvent<ResourceModel, CallbackContext> createDomain(
        AmazonWebServicesClientProxy proxy,
        ProgressEvent<ResourceModel, CallbackContext> progress,
        ResourceHandlerRequest<ResourceModel> request,
        ProxyClient<CodeartifactClient> proxyClient
    ) {
        return proxy.initiate("AWS-CodeArtifact-Domain::Create", proxyClient,progress.getResourceModel(), progress.getCallbackContext())
            .translateToServiceRequest(Translator::translateToCreateRequest)
            .makeServiceCall((awsRequest, client) -> createDomainSdkCall(progress, client, awsRequest))
            .stabilize((awsRequest, awsResponse, client, model, context) -> isStabilized(model, client, request))
            .progress();
    }

    private CreateDomainResponse createDomainSdkCall(
        ProgressEvent<ResourceModel, CallbackContext>  progress,
        ProxyClient<CodeartifactClient> client,
        CreateDomainRequest awsRequest
    ) {
        CreateDomainResponse awsResponse = null;
        String domainName = progress.getResourceModel().getDomainName();
        try {
            awsResponse = client.injectCredentialsAndInvokeV2(awsRequest, client.client()::createDomain);
        } catch (final AwsServiceException e) {
            Translator.throwCfnException(e, Constants.CREATE_DOMAIN, domainName);
        }

        logger.log(String.format("%s successfully created.", ResourceModel.TYPE_NAME));
        return awsResponse;
    }

    private boolean isStabilized(
        final ResourceModel model,
        final ProxyClient<CodeartifactClient> proxyClient,
        ResourceHandlerRequest<ResourceModel> request
    ) {
        DescribeDomainResponse describeDomainResponse = proxyClient.injectCredentialsAndInvokeV2(
            Translator.translateToReadRequest(model, request), proxyClient.client()::describeDomain);

        String domainStatus = describeDomainResponse.domain()
            .status()
            .toString();

        model.setArn(describeDomainResponse.domain().arn());
        logger.log(String.format("Status of domain: %s", domainStatus));

        if (domainStatus.equals(Constants.ACTIVE_STATUS)) {
            logger.log(String.format("%s successfully stabilized.", ResourceModel.TYPE_NAME));
            return true;
        }

        return false;
    }

}
