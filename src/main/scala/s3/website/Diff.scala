package s3.website

import s3.website.model._

object Diff {
  def resolveDiff(localFiles: Seq[LocalFile], s3Files: Seq[S3File])(implicit config: Config):
  Stream[Either[Error, Upload with UploadTypeResolved]] = {
    val remoteS3KeysIndex = s3Files.map(_.s3Key).toSet
    val remoteMd5Index = s3Files.map(_.md5).toSet
    localFiles
      .toStream // Load lazily, because the MD5 computation for the local file requires us to read the whole file
      .map(resolveUploadSource)
      .collect {
      case errorOrUpload if errorOrUpload.right.exists(isNewUpload(remoteS3KeysIndex)) =>
        for (upload <- errorOrUpload.right) yield upload withUploadType NewFile
      case errorOrUpload if errorOrUpload.right.exists(isUpdate(remoteS3KeysIndex, remoteMd5Index)) =>
        for (upload <- errorOrUpload.right) yield upload withUploadType Update
    }
  }


  def isNewUpload(remoteS3KeysIndex: Set[String])(u: Upload) = !remoteS3KeysIndex.exists(_ == u.s3Key)

  def isUpdate(remoteS3KeysIndex: Set[String], remoteMd5Index: Set[String])(u: Upload) =
    remoteS3KeysIndex.exists(_ == u.s3Key) && !remoteMd5Index.exists(remoteMd5 => u.essence.right.exists(_.md5 == remoteMd5))

  def resolveUploadSource(localFile: LocalFile)(implicit config: Config): Either[Error, Upload] =
    for (upload <- LocalFile.toUpload(localFile).right)
    yield upload
}