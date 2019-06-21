package com.ingenico.connect.gateway.sdk.java.merchant.payments;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Arrays;

import org.apache.http.impl.io.EmptyInputStream;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import com.ingenico.connect.gateway.sdk.java.ApiException;
import com.ingenico.connect.gateway.sdk.java.CallContext;
import com.ingenico.connect.gateway.sdk.java.Client;
import com.ingenico.connect.gateway.sdk.java.CommunicationException;
import com.ingenico.connect.gateway.sdk.java.Connection;
import com.ingenico.connect.gateway.sdk.java.DeclinedPaymentException;
import com.ingenico.connect.gateway.sdk.java.Factory;
import com.ingenico.connect.gateway.sdk.java.GlobalCollectException;
import com.ingenico.connect.gateway.sdk.java.IdempotenceException;
import com.ingenico.connect.gateway.sdk.java.MetaDataProvider;
import com.ingenico.connect.gateway.sdk.java.NotFoundException;
import com.ingenico.connect.gateway.sdk.java.ReferenceException;
import com.ingenico.connect.gateway.sdk.java.RequestHeader;
import com.ingenico.connect.gateway.sdk.java.ResponseException;
import com.ingenico.connect.gateway.sdk.java.ResponseHandler;
import com.ingenico.connect.gateway.sdk.java.ResponseHeader;
import com.ingenico.connect.gateway.sdk.java.Session;
import com.ingenico.connect.gateway.sdk.java.ValidationException;
import com.ingenico.connect.gateway.sdk.java.defaultimpl.AuthorizationType;
import com.ingenico.connect.gateway.sdk.java.defaultimpl.DefaultAuthenticator;
import com.ingenico.connect.gateway.sdk.java.domain.definitions.Address;
import com.ingenico.connect.gateway.sdk.java.domain.definitions.AmountOfMoney;
import com.ingenico.connect.gateway.sdk.java.domain.definitions.Card;
import com.ingenico.connect.gateway.sdk.java.domain.payment.CreatePaymentRequest;
import com.ingenico.connect.gateway.sdk.java.domain.payment.CreatePaymentResponse;
import com.ingenico.connect.gateway.sdk.java.domain.payment.definitions.CardPaymentMethodSpecificInput;
import com.ingenico.connect.gateway.sdk.java.domain.payment.definitions.Customer;
import com.ingenico.connect.gateway.sdk.java.domain.payment.definitions.Order;

@RunWith(MockitoJUnitRunner.class)
public class PaymentsClientTest {

	private Session session;

	@Mock private Connection connection;

	@Before
	public void initializeSession() {
		URI apiEndpoint = URI.create("http://localhost");
		session = new Session(apiEndpoint, connection, new DefaultAuthenticator(AuthorizationType.V1HMAC, "test", "test"), new MetaDataProvider("Ingenico"));
	}

	/**
	 * Tests that a non-failure response will not throw an exception.
	 */
	@Test
	@SuppressWarnings("resource")
	public void testCreateSuccess() {

		Client client = Factory.createClient(session);
		String responseBody = getResource("pending_approval.json");
		mockPost(201, responseBody);

		CreatePaymentRequest body = createRequest();

		CreatePaymentResponse response = client.merchant("merchantId").payments().create(body);
		Assert.assertEquals("000002000020142549460000100001", response.getPayment().getId());
		Assert.assertEquals("PENDING_APPROVAL", response.getPayment().getStatus());
	}

	/**
	 * Tests that a failure response with a payment result will throw a {@link DeclinedPaymentException}.
	 */
	@Test
	@SuppressWarnings("resource")
	public void testCreateRejected() {

		Client client = Factory.createClient(session);
		String responseBody = getResource("rejected.json");
		mockPost(400, responseBody);

		CreatePaymentRequest body = createRequest();

		try {
			client.merchant("merchantId").payments().create(body);
			Assert.fail("Expected DeclinedPaymentException");
		} catch (DeclinedPaymentException e) {
			Assert.assertTrue(e.toString().contains("payment '000002000020142544360000100001'"));
			Assert.assertTrue(e.toString().contains("status 'REJECTED'"));
			Assert.assertTrue(e.toString().contains(responseBody));
			Assert.assertNotNull(e.getCreatePaymentResult());
			Assert.assertEquals("000002000020142544360000100001", e.getCreatePaymentResult().getPayment().getId());
			Assert.assertEquals("REJECTED", e.getCreatePaymentResult().getPayment().getStatus());
		}
	}

	/**
	 * Tests that a 400 failure response without a payment result will throw a {@link ValidationException}.
	 */
	@Test
	@SuppressWarnings("resource")
	public void testCreateInvalidRequest() {

		Client client = Factory.createClient(session);
		String responseBody = getResource("invalid_request.json");
		mockPost(400, responseBody);

		CreatePaymentRequest body = createRequest();

		try {
			client.merchant("merchantId").payments().create(body);
			Assert.fail("Expected ValidationException");
		} catch (ValidationException e) {
			Assert.assertTrue(e.toString().contains(responseBody));
		}
	}

	/**
	 * Tests that a 401 failure response without a payment result will throw a {@link ApiException}.
	 */
	@Test
	@SuppressWarnings("resource")
	public void testCreateInvalidAuthorization() {

		Client client = Factory.createClient(session);
		String responseBody = getResource("invalid_authorization.json");
		mockPost(401, responseBody);

		CreatePaymentRequest body = createRequest();

		try {
			client.merchant("merchantId").payments().create(body);
			Assert.fail("Expected ApiException");
		} catch (ApiException e) {
			Assert.assertTrue(e.toString().contains(responseBody));
		}
	}

	/**
	 * Tests that a 409 failure response with a duplicate request code but without an idempotence key will throw a
	 * {@link ReferenceException}.
	 */
	@Test
	@SuppressWarnings("resource")
	public void testCreateReferenceError() {

		Client client = Factory.createClient(session);
		String responseBody = getResource("duplicate_request.json");
		mockPost(409, responseBody);

		CreatePaymentRequest body = createRequest();

		try {
			client.merchant("merchantId").payments().create(body);
			Assert.fail("Expected ApiException");
		} catch (ReferenceException e) {
			Assert.assertTrue(e.toString().contains(responseBody));
		}
	}

	/**
	 * Tests that a 409 failure response with a duplicate request code and an idempotence key will throw an {@link IdempotenceException}.
	 */
	@Test
	@SuppressWarnings("resource")
	public void testCreateIdempotenceError() {

		Client client = Factory.createClient(session);
		String responseBody = getResource("duplicate_request.json");
		mockPost(409, responseBody);

		CreatePaymentRequest body = createRequest();

		CallContext context = new CallContext().withIdempotenceKey("key");

		try {
			client.merchant("merchantId").payments().create(body, context);
			Assert.fail("Expected ApiException");
		} catch (IdempotenceException e) {
			Assert.assertTrue(e.toString().contains(responseBody));
			Assert.assertEquals(context.getIdempotenceKey(), e.getIdempotenceKey());
		}
	}

	/**
	 * Tests that a 404 response with a non-JSON response will throw a {@link NotFoundException}.
	 */
	@Test
	@SuppressWarnings("resource")
	public void testCreateNotFound() {

		Client client = Factory.createClient(session);
		String responseBody = getResource("not_found.html");
		mockPost(404, responseBody, new ResponseHeader("content-type", "text/html"));

		CreatePaymentRequest body = createRequest();

		try {
			client.merchant("merchantId").payments().create(body);
			Assert.fail("Expected NotFoundException");
		} catch (NotFoundException e) {
			Assert.assertNotNull(e.getCause());
			Assert.assertEquals(ResponseException.class, e.getCause().getClass());
			Assert.assertTrue(e.getCause().toString().contains(responseBody));
		}
	}

	/**
	 * Tests that a 405 response with a non-JSON response will throw a {@link CommunicationException}.
	 */
	@Test
	@SuppressWarnings("resource")
	public void testCreateMethodNotAllowed() {

		Client client = Factory.createClient(session);
		String responseBody = getResource("method_not_allowed.html");
		mockPost(405, responseBody, new ResponseHeader("content-type", "text/html"));

		CreatePaymentRequest body = createRequest();

		try {
			client.merchant("merchantId").payments().create(body);
			Assert.fail("Expected CommunicationException");
		} catch (CommunicationException e) {
			Assert.assertNotNull(e.getCause());
			Assert.assertEquals(ResponseException.class, e.getCause().getClass());
			Assert.assertTrue(e.getCause().toString().contains(responseBody));
		}
	}

	/**
	 * Tests that a 500 response with a JSON response with no body will throw a {@link GlobalCollectException} and not a {@link NullPointerException}.
	 */
	@Test
	@SuppressWarnings("resource")
	public void testCreateInternalServerErrorWithoutBody() {

		Client client = Factory.createClient(session);
		String responseBody = null;
		mockPost(500, responseBody, new ResponseHeader("content-type", "text/html"));

		CreatePaymentRequest body = createRequest();

		try {
			client.merchant("merchantId").payments().create(body);
			Assert.fail("Expected GlobalCollectException");
		} catch (GlobalCollectException e) {
			Assert.assertEquals("", e.getResponseBody());
			Assert.assertNull(e.getErrorId());
			Assert.assertEquals(0, e.getErrors().size());
		}
	}

	@SuppressWarnings({ "unchecked", "resource" })
	private <R> void mockPost(final int statusCode, final String responseBody, final ResponseHeader... headers) {
		when(connection.post(any(URI.class), anyListOf(RequestHeader.class), anyString(), any(ResponseHandler.class))).thenAnswer(new Answer<R>() {
			@Override
			public R answer(InvocationOnMock invocation) throws Throwable {
				ResponseHandler<R> responseHandler = invocation.getArgumentAt(3, ResponseHandler.class);
				InputStream bodyStream = responseBody != null ? toInputStream(responseBody) : EmptyInputStream.INSTANCE;
				return responseHandler.handleResponse(statusCode, bodyStream, Arrays.asList(headers));
			}
		});
	}

	private String getResource(String resource) {
		StringWriter sw = new StringWriter();
		try {
			Reader reader = new InputStreamReader(getClass().getResourceAsStream(resource), Charset.forName("UTF-8"));
			try {
				char[] buffer = new char[1024];
				int len;
				while ((len = reader.read(buffer)) != -1) {
					sw.write(buffer, 0, len);
				}
			} finally {
				reader.close();
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return sw.toString();
	}

	private InputStream toInputStream(String content) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		OutputStreamWriter output = new OutputStreamWriter(baos, Charset.forName("UTF-8"));
		try {
			output.write(content);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			try {
				output.close();
			} catch (@SuppressWarnings("unused") IOException e) {
				// ignore
			}
		}
		return new ByteArrayInputStream(baos.toByteArray());
	}

	private CreatePaymentRequest createRequest() {

		CreatePaymentRequest body = new CreatePaymentRequest();

		Order order = new Order();

		AmountOfMoney amountOfMoney = new AmountOfMoney();
		amountOfMoney.setAmount(2345L);
		amountOfMoney.setCurrencyCode("CAD");
		order.setAmountOfMoney(amountOfMoney);

		Customer customer = new Customer();

		Address billingAddress = new Address();
		billingAddress.setCountryCode("CA");
		customer.setBillingAddress(billingAddress);

		order.setCustomer(customer);

		CardPaymentMethodSpecificInput cardPaymentMethodSpecificInput = new CardPaymentMethodSpecificInput();
		cardPaymentMethodSpecificInput.setPaymentProductId(1);

		Card card = new Card();
		card.setCvv("123");
		card.setCardNumber("4567350000427977");
		card.setExpiryDate("1220");
		cardPaymentMethodSpecificInput.setCard(card);

		body.setCardPaymentMethodSpecificInput(cardPaymentMethodSpecificInput);

		return body;
	}
}
