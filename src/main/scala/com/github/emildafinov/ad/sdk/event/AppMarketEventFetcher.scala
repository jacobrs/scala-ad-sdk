package com.github.emildafinov.ad.sdk.event

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods.GET
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.stream.Materializer
import akka.util.ByteString
import com.github.emildafinov.ad.sdk.authentication.{AuthorizationTokenGenerator, AppMarketCredentialsSupplier, AppMarketCredentials, UnknownClientKeyException}
import com.github.emildafinov.ad.sdk.payload.Event
import org.json4s._
import org.json4s.jackson.JsonMethods._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.control.NonFatal
/**
  * Returns the event id and payload.
  * Synchronous, because we always want to retrieve the event before releasing the connections
  * @param credentialsSupplier         for retrieving the client secret needed to generate the bearer token
  * @param authorizationTokenGenerator used to generate the OAuth bearer token used to sign the request
  * @param appMarketTimeoutInterval    timeout for retrieving the event payload.
  */
class AppMarketEventFetcher(credentialsSupplier: AppMarketCredentialsSupplier,
                            authorizationTokenGenerator: AuthorizationTokenGenerator,
                            appMarketTimeoutInterval: FiniteDuration = 15 seconds)
                           (implicit as: ActorSystem,
                            am: Materializer,
                            ec: ExecutionContext) {

  implicit val formats = DefaultFormats
  
  def fetchRawAppMarketEvent(clientCredentials: AppMarketCredentials, eventFetchUrl: String): (String, Event) = {
  
    val parsedRawEvent = (for {
      eventFetchRequest <- signedFetchRequest(eventFetchUrl, clientCredentials.clientKey())
      response <- Http().singleRequest(eventFetchRequest)
      responseBody <- response.entity.dataBytes.runFold(ByteString(""))(_ ++ _)
      responseBodyString = responseBody.decodeString("utf8")
    } yield parse(responseBodyString).extract[Event]) map { eventPayload =>

      extractIdFrom(eventFetchUrl) -> eventPayload

    } recover {
      case NonFatal(e) => throw new CouldNotFetchRawMarketplaceEventException(e)
    }

    Await.result(
      awaitable = parsedRawEvent,
      atMost = appMarketTimeoutInterval
    )
  }

  private def signedFetchRequest(eventFetchUrl: String, clientKey: String) = Future {
    val marketplaceCredentials = credentialsSupplier
      .readCredentialsFor(clientKey)
        .orElseThrow(() => new UnknownClientKeyException)

    val bearerTokenValue = authorizationTokenGenerator.generateAuthorizationHeader(
      "GET",
      eventFetchUrl,
      marketplaceCredentials
    )

    HttpRequest(
      method = GET,
      uri = eventFetchUrl,
      headers = List(Authorization(OAuth2BearerToken(bearerTokenValue)))
    )
  }

  private def extractIdFrom(eventFetchUrl: String): String = eventFetchUrl split "/" last

}


