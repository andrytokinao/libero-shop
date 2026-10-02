/** The side of a profile photo as stored: sharp on the largest avatar, a few tens of KB. */
export const PROFILE_PHOTO_SIZE = 256;

/**
 * A picture cut to a centred square and shrunk to `size` pixels, as a JPEG.
 *
 * <p>Done in the browser, before sending: a phone photo is several megabytes, and the server
 * keeps only a thumbnail (it refuses anything over 512 KB). The square is taken from the middle,
 * which is where a face usually is.
 *
 * @throws Error with a message ready to show when the file is not an image the browser can read
 */
export async function squareThumbnail(file: Blob, size = PROFILE_PHOTO_SIZE): Promise<Blob> {
  let bitmap: ImageBitmap;
  try {
    bitmap = await createImageBitmap(file);
  } catch {
    throw new Error("Ce fichier n'est pas une image lisible.");
  }
  const side = Math.min(bitmap.width, bitmap.height);
  const canvas = document.createElement('canvas');
  canvas.width = size;
  canvas.height = size;
  const context = canvas.getContext('2d');
  if (!context) {
    throw new Error("Le navigateur ne peut pas préparer l'image.");
  }
  context.imageSmoothingQuality = 'high';
  context.drawImage(
    bitmap,
    (bitmap.width - side) / 2,
    (bitmap.height - side) / 2,
    side,
    side,
    0,
    0,
    size,
    size,
  );
  bitmap.close();

  return new Promise((resolve, reject) =>
    canvas.toBlob(
      (blob) => (blob ? resolve(blob) : reject(new Error("L'image n'a pas pu être préparée."))),
      'image/jpeg',
      0.85,
    ),
  );
}
