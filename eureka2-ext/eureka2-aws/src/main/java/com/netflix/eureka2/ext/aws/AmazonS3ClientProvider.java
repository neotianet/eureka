package com.netflix.eureka2.ext.aws;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3Client;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Provider;

/**
* @author David Liu
*/
public class AmazonS3ClientProvider implements Provider<AmazonS3Client> {

    private final AmazonS3Client amazonS3Client;

    @Inject
    public AmazonS3ClientProvider(AwsConfiguration configuration) {
        if (configuration.getAwsAccessId() != null && configuration.getAwsSecretKey() != null) {
            amazonS3Client = new AmazonS3Client(new BasicAWSCredentials(configuration.getAwsAccessId(), configuration.getAwsSecretKey()));
        } else {
            amazonS3Client = new AmazonS3Client(new InstanceProfileCredentialsProvider());
        }

        String region = configuration.getRegion().trim().toLowerCase();
        if (region.equals("us-east-1")) {
            amazonS3Client.setEndpoint("s3.amazonaws.com");
        } else {
            amazonS3Client.setEndpoint("s3-" + region + ".amazonaws.com");
        }
    }

    @Override
    public AmazonS3Client get() {
        return amazonS3Client;
    }

    @PreDestroy
    public void shutdown() {
        amazonS3Client.shutdown();
    }
}