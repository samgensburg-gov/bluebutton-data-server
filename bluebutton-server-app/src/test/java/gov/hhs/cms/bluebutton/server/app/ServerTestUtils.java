package gov.hhs.cms.bluebutton.server.app;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.net.ssl.SSLContext;

import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContexts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricRegistry;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.IGenericClient;
import gov.hhs.cms.bluebutton.data.model.rif.Beneficiary;
import gov.hhs.cms.bluebutton.data.model.rif.RifFileEvent;
import gov.hhs.cms.bluebutton.data.model.rif.RifFileRecords;
import gov.hhs.cms.bluebutton.data.model.rif.RifFilesEvent;
import gov.hhs.cms.bluebutton.data.model.rif.samples.StaticRifResource;
import gov.hhs.cms.bluebutton.data.model.rif.schema.DatabaseSchemaManager;
import gov.hhs.cms.bluebutton.data.pipeline.rif.load.LoadAppOptions;
import gov.hhs.cms.bluebutton.data.pipeline.rif.load.RifLoader;
import gov.hhs.cms.bluebutton.data.pipeline.rif.load.RifLoaderTestUtils;
import gov.hhs.cms.bluebutton.datapipeline.rif.extract.RifFilesProcessor;

/**
 * Contains test utilities.
 */
public final class ServerTestUtils {
	private static final Logger LOGGER = LoggerFactory.getLogger(ServerTestUtils.class);

	/**
	 * @return a new FHIR {@link IGenericClient} for use
	 */
	public static IGenericClient createFhirClient() {
		FhirContext ctx = FhirContext.forDstu3();

		/*
		 * The default timeout is 10s, which was failing for batches of 100. A
		 * 300s timeout was failing for batches of 100 once Part B claims were
		 * mostly mapped, so batches were cut to 10, which ran at 12s or so,
		 * each.
		 */
		ctx.getRestfulClientFactory().setSocketTimeout(300 * 1000);

		/*
		 * We need to override the FHIR client's SSLContext. Unfortunately, that
		 * requires overriding the entire HttpClient that it uses. Otherwise,
		 * the settings used here mirror those that the default FHIR HttpClient
		 * would use.
		 */
		try {
			SSLContext sslContext = SSLContexts.custom()
					.loadKeyMaterial(getClientKeyStorePath().toFile(), "changeit".toCharArray(),
							"changeit".toCharArray())
					.loadTrustMaterial(getClientTrustStorePath().toFile(), "changeit".toCharArray()).build();
			PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager(
					RegistryBuilder.<ConnectionSocketFactory> create()
							.register("http", PlainConnectionSocketFactory.getSocketFactory())
							.register("https", new SSLConnectionSocketFactory(sslContext)).build(),
					null, null, null, 5000, TimeUnit.MILLISECONDS);
			@SuppressWarnings("deprecation")
			RequestConfig defaultRequestConfig = RequestConfig.custom()
					.setSocketTimeout(ctx.getRestfulClientFactory().getSocketTimeout())
					.setConnectTimeout(ctx.getRestfulClientFactory().getConnectTimeout())
					.setConnectionRequestTimeout(ctx.getRestfulClientFactory().getConnectionRequestTimeout())
					.setStaleConnectionCheckEnabled(true).build();
			HttpClient httpClient = HttpClients.custom().setConnectionManager(connectionManager)
					.setDefaultRequestConfig(defaultRequestConfig).disableCookieManagement().build();
			ctx.getRestfulClientFactory().setHttpClient(httpClient);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} catch (KeyManagementException | UnrecoverableKeyException | NoSuchAlgorithmException | KeyStoreException
				| CertificateException e) {
			throw new IllegalStateException(e);
		}

		IGenericClient client = ctx.newRestfulGenericClient("https://localhost:9094/baseDstu3".toString());
		return client;
	}

	/**
	 * @return the local {@link Path} to the key store that FHIR clients should
	 *         use
	 */
	private static Path getClientKeyStorePath() {
		/*
		 * The working directory for tests will either be the module directory
		 * or their parent directory. With that knowledge, we're searching for
		 * the ssl-stores directory.
		 */
		Path sslStoresDir = Paths.get("..", "dev", "ssl-stores");
		if (!Files.isDirectory(sslStoresDir))
			sslStoresDir = Paths.get("dev", "ssl-stores");
		if (!Files.isDirectory(sslStoresDir))
			throw new IllegalStateException();

		Path keyStorePath = sslStoresDir.resolve("client.keystore");
		return keyStorePath;
	}

	/**
	 * @return the local {@link Path} to the trust store that FHIR clients
	 *         should use
	 */
	private static Path getClientTrustStorePath() {
		Path trustStorePath = getClientKeyStorePath().getParent().resolve("client.truststore");
		return trustStorePath;
	}

	/**
	 * Ensures that the database used in tests has the correct database schema.
	 */
	public static void createDatabaseSchema() {
		LoadAppOptions options = createRifLoaderOptions();
		DatabaseSchemaManager.createOrUpdateSchema(RifLoaderTestUtils.createDataSouce(options));
	}

	/**
	 * @param sampleResources
	 *            the sample RIF resources to parse
	 * @return the {@link List} of RIF records that were parsed (e.g.
	 *         {@link Beneficiary}s, etc.)
	 */
	public static List<Object> parseData(List<StaticRifResource> sampleResources) {
		RifFilesEvent rifFilesEvent = new RifFilesEvent(Instant.now(),
				sampleResources.stream().map(r -> r.toRifFile()).collect(Collectors.toList()));
		RifFilesProcessor processor = new RifFilesProcessor();
		List<Object> recordsParsed = new ArrayList<>();
		for (RifFileEvent rifFileEvent : rifFilesEvent.getFileEvents()) {
			RifFileRecords rifFileRecords = processor.produceRecords(rifFileEvent);
			rifFileRecords.getRecords().map(r -> r.getRecord()).forEach(r -> recordsParsed.add(r));
		}

		return recordsParsed;
	}

	/**
	 * @param sampleResources
	 *            the sample RIF resources to load
	 * @return the {@link List} of RIF records that were loaded (e.g.
	 *         {@link Beneficiary}s, etc.)
	 */
	public static List<Object> loadData(List<StaticRifResource> sampleResources) {
		LoadAppOptions loadOptions = createRifLoaderOptions();
		RifFilesEvent rifFilesEvent = new RifFilesEvent(Instant.now(),
				sampleResources.stream().map(r -> r.toRifFile()).collect(Collectors.toList()));

		// Create the processors that will handle each stage of the pipeline.
		MetricRegistry loadAppMetrics = new MetricRegistry();
		RifFilesProcessor processor = new RifFilesProcessor();
		RifLoader loader = new RifLoader(loadAppMetrics, loadOptions);

		// Link up the pipeline and run it.
		LOGGER.info("Loading RIF records...");
		List<Object> recordsLoaded = new ArrayList<>();
		for (RifFileEvent rifFileEvent : rifFilesEvent.getFileEvents()) {
			RifFileRecords rifFileRecords = processor.produceRecords(rifFileEvent);
			loader.process(rifFileRecords, error -> {
				LOGGER.warn("Record(s) failed to load.", error);
			}, result -> {
				recordsLoaded.add(result.getRifRecordEvent().getRecord());
			});
		}
		LOGGER.info("Loaded RIF records: '{}'.");
		return recordsLoaded;
	}

	/**
	 * Calls {@link RifLoaderTestUtils#cleanDatabaseServer(LoadAppOptions)}.
	 */
	public static void cleanDatabaseServer() {
		RifLoaderTestUtils.cleanDatabaseServerViaDeletes(createRifLoaderOptions());
	}

	/**
	 * @return the {@link LoadAppOptions} to use with {@link RifLoader}
	 */
	private static LoadAppOptions createRifLoaderOptions() {
		return new LoadAppOptions(RifLoaderTestUtils.HICN_HASH_ITERATIONS, RifLoaderTestUtils.HICN_HASH_PEPPER,
				"jdbc:hsqldb:hsql://localhost/test-embedded", "test", "test".toCharArray(),
				LoadAppOptions.DEFAULT_LOADER_THREADS);
	}
}
