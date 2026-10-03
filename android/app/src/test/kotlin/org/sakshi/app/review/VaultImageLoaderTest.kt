package org.sakshi.app.review

import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.robolectric.shadows.ShadowBitmapFactory

class VaultImageLoaderTest : PictureTestBase() {
    /** Robolectric otherwise answers any bytes with a blank bitmap; a phone refuses them. */
    @BeforeTest
    fun refuseInvalidImageData() = ShadowBitmapFactory.setAllowInvalidImageData(false)

    private fun loader(maxLongSide: Int = VaultImageLoader.MAX_LONG_SIDE) = VaultImageLoader(vault.evidence, maxLongSide = maxLongSide)

    private fun load(id: String, loader: VaultImageLoader = loader()) = runBlocking { loader.load(id) }

    private fun listing(directory: File): List<String> = directory.walkTopDown().filter { it.isFile }.map { it.relativeTo(directory).path }.sorted().toList()

    @Test
    fun aSmallPictureIsDecodedAtItsOwnSize() {
        val id = importPicture(newCase(), SyntheticPictures.png(300, 100))
        val image = assertIs<ImageLoadResult.Loaded>(load(id)).image
        assertEquals(300, image.bitmap.width)
        assertEquals(100, image.bitmap.height)
        assertEquals(300, image.uprightWidth)
        assertEquals(100, image.uprightHeight)
    }

    @Test
    fun aLargePictureIsReducedToTheBoundOnItsLongerSide() {
        val id = importPicture(newCase(), SyntheticPictures.png(5000, 400))
        val image = assertIs<ImageLoadResult.Loaded>(load(id)).image
        assertTrue(maxOf(image.bitmap.width, image.bitmap.height) <= VaultImageLoader.MAX_LONG_SIDE, "decoded ${image.bitmap.width}x${image.bitmap.height}")
        assertTrue(image.bitmap.width >= VaultImageLoader.MAX_LONG_SIDE / 2, "reduced no more than needed")
        assertEquals(5000, image.uprightWidth, "the original size is still known")
        assertEquals(400, image.uprightHeight)
    }

    @Test
    fun theSampleSizeIsThePowerOfTwoThatFitsTheBound() {
        val loader = loader(maxLongSide = 100)
        assertEquals(1, loader.sampleSizeFor(100, 50))
        assertEquals(2, loader.sampleSizeFor(101, 50))
        assertEquals(4, loader.sampleSizeFor(300, 50))
        assertEquals(8, loader.sampleSizeFor(50, 799))
    }

    @Test
    fun aPictureDeclaringMoreThanTheLimitsIsRefusedWithoutDecoding() {
        val id = importPicture(newCase(), SyntheticPictures.png(300, 100))
        assertEquals(ImageLoadResult.TooLarge, load(id, VaultImageLoader(vault.evidence, maxDeclaredSide = 299)), "a side beyond the limit")
        assertEquals(ImageLoadResult.TooLarge, load(id, VaultImageLoader(vault.evidence, maxDeclaredPixels = 29_999L)), "too many pixels in all")
        assertIs<ImageLoadResult.Loaded>(load(id, VaultImageLoader(vault.evidence, maxDeclaredSide = 300, maxDeclaredPixels = 30_000L)))
    }

    @Test
    fun theDefaultLimitsRefuseAbsurdSizesButAllowPhoneSizedPictures() {
        assertTrue(VaultImageLoader.MAX_DECLARED_SIDE < 100_000)
        assertTrue(12_000L * 9_000L <= VaultImageLoader.MAX_DECLARED_PIXELS, "a 108 megapixel phone camera is allowed")
        assertTrue(20_000L * 20_000L > VaultImageLoader.MAX_DECLARED_PIXELS)
    }

    @Test
    fun aQuarterTurnOrientationIsAppliedAndTheUprightSizeReported() {
        val id = importPicture(newCase(), SyntheticPictures.jpeg(200, 100, orientation = 6), "image/jpeg")
        val image = assertIs<ImageLoadResult.Loaded>(load(id)).image
        assertEquals(100, image.bitmap.width)
        assertEquals(200, image.bitmap.height)
        assertEquals(100, image.uprightWidth)
        assertEquals(200, image.uprightHeight)
    }

    @Test
    fun aPictureWithoutAnOrientationIsLeftAsStored() {
        val id = importPicture(newCase(), SyntheticPictures.jpeg(200, 100, orientation = 1), "image/jpeg")
        val image = assertIs<ImageLoadResult.Loaded>(load(id)).image
        assertEquals(200, image.bitmap.width)
        assertEquals(100, image.uprightHeight)
    }

    @Test
    fun bytesThatAreNotAnImageAreUnreadable() {
        val id = importPicture(newCase(), ByteArray(64) { 7 })
        assertEquals(ImageLoadResult.Unreadable, load(id))
    }

    @Test
    fun aDamagedBlobIsUnreadableAndNothingIsShown() {
        val id = importPicture(newCase(), SyntheticPictures.png(300, 100))
        val blobs = File(context.noBackupFilesDir, "vault/blobs").walkTopDown().filter { it.isFile && it.extension != "tmp" }.toList()
        val blob = blobs.single()
        val bytes = blob.readBytes()
        bytes[bytes.size - 3] = (bytes[bytes.size - 3].toInt() xor 0x55).toByte()
        blob.writeBytes(bytes)
        assertEquals(ImageLoadResult.Unreadable, load(id))
    }

    @Test
    fun anUnknownEvidenceIdIsUnreadable() {
        assertEquals(ImageLoadResult.Unreadable, load("synthetic-missing"))
    }

    @Test
    fun loadingWritesNoFileToTheAppFoldersOrTheVault() {
        val id = importPicture(newCase(), SyntheticPictures.jpeg(400, 200, orientation = 6), "image/jpeg")
        val folders = listOfNotNull(context.cacheDir, context.filesDir, context.codeCacheDir, context.noBackupFilesDir, context.externalCacheDir)
        val before = folders.associateWith { listing(it) }
        assertIs<ImageLoadResult.Loaded>(load(id))
        load("synthetic-missing")
        assertEquals(before, folders.associateWith { listing(it) })
    }
}
