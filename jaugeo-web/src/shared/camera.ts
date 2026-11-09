/** API du navigateur (Chrome Android, Edge) : absente des types DOM standard. */
interface DetecteurCodes {
  detect(source: HTMLVideoElement): Promise<{ rawValue: string }[]>
}
type ConstructeurDetecteur = new (options: { formats: string[] }) => DetecteurCodes

export function constructeurDetecteur(): ConstructeurDetecteur | null {
  const w = window as unknown as { BarcodeDetector?: ConstructeurDetecteur }
  return w.BarcodeDetector ?? null
}

/** La caméra peut-elle lire un QR code sur cet appareil ? */
export const lectureCameraDisponible = () =>
  typeof window !== 'undefined' && !!constructeurDetecteur() && !!navigator.mediaDevices?.getUserMedia
