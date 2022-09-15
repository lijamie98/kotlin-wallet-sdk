package org.stellar.walletsdk

import org.stellar.sdk.*
import org.stellar.walletsdk.util.buildTransaction
import org.stellar.walletsdk.util.sponsorOperation

class Wallet(
  private val horizonUrl: String = "https://horizon-testnet.stellar.org",
  private val networkPassphrase: String = Network.TESTNET.toString()
) {
  private val server = Server(this.horizonUrl)
  private val network = Network(this.networkPassphrase)

  data class AccountKeypair(val publicKey: String, val secretKey: String)

  // Create account keys, generate new keypair
  fun create(): AccountKeypair {
    val keypair: KeyPair = KeyPair.random()
    return AccountKeypair(keypair.accountId, String(keypair.secretSeed))
  }

  // Fund (activate) account
  // TODO: ??? do we need to include add trustline operation here?
  fun fund(
    sourceAddress: String,
    destinationAddress: String,
    startingBalance: String = "1",
    sponsorAddress: String = ""
  ): Transaction {
    val isSponsored = sponsorAddress.isNotBlank()

    if (!isSponsored && startingBalance.toInt() < 1) {
      throw Exception("Starting balance must be at least 1 XLM for non-sponsored accounts")
    }

    val startBalance = if (isSponsored) "0" else startingBalance

    val createAccountOp: CreateAccountOperation =
      CreateAccountOperation.Builder(destinationAddress, startBalance)
        .setSourceAccount(sourceAddress)
        .build()

    val operations: List<Operation> =
      if (isSponsored) {
        sponsorOperation(sponsorAddress, destinationAddress, createAccountOp)
      } else {
        listOfNotNull(createAccountOp)
      }

    return buildTransaction(sourceAddress, server, network, operations)
  }

  // Add trustline
  fun addAssetSupport(
    sourceAddress: String,
    assetCode: String,
    assetIssuer: String,
    trustLimit: String = Long.MAX_VALUE.toBigDecimal().movePointLeft(7).toPlainString(),
    sponsorAddress: String = ""
  ): Transaction {
    val isSponsored = sponsorAddress.isNotBlank()

    val asset = ChangeTrustAsset.createNonNativeAsset(assetCode, assetIssuer)
    val changeTrustOp: ChangeTrustOperation =
      ChangeTrustOperation.Builder(asset, trustLimit).setSourceAccount(sourceAddress).build()

    val operations: List<Operation> =
      if (isSponsored) {
        sponsorOperation(sponsorAddress, sourceAddress, changeTrustOp)
      } else {
        listOfNotNull(changeTrustOp)
      }

    return buildTransaction(sourceAddress, server, network, operations)
  }

  // Remove trustline
  fun removeAssetSupport(
    sourceAddress: String,
    assetCode: String,
    assetIssuer: String
  ): Transaction {
    return addAssetSupport(sourceAddress, assetCode, assetIssuer, "0")
  }

  // Add signer
  fun addAccountSigner(
    sourceAddress: String,
    signerAddress: String,
    signerWeight: Int,
    sponsorAddress: String = ""
  ): Transaction {
    val isSponsored = sponsorAddress.isNotBlank()
    val keyPair = KeyPair.fromAccountId(signerAddress)
    val signer = Signer.ed25519PublicKey(keyPair)

    val addSignerOp: SetOptionsOperation =
      SetOptionsOperation.Builder().setSigner(signer, signerWeight).build()

    val operations: List<Operation> =
      if (isSponsored) {
        sponsorOperation(sponsorAddress, sourceAddress, addSignerOp)
      } else {
        listOfNotNull(addSignerOp)
      }

    return buildTransaction(sourceAddress, server, network, operations)
  }

  // Remove signer
  fun removeAccountSigner(sourceAddress: String, signerAddress: String): Transaction {
    return addAccountSigner(sourceAddress, signerAddress, 0)
  }

  // Submit transaction
  fun submitTransaction(signedTransaction: Transaction, serverInstance: Server = server): Boolean {
    val response = serverInstance.submitTransaction(signedTransaction)

    if (response.isSuccess) {
      return true
    }

    var errorMessage = "Transaction failed"

    if (!response.extras?.resultCodes?.transactionResultCode.isNullOrBlank()) {
      errorMessage += ": ${response.extras.resultCodes.transactionResultCode}"
    }

    throw Exception(errorMessage)
  }
}
