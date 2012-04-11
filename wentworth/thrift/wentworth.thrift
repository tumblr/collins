namespace java com.tumblr.wentworth.thrift
namespace rb Wentworth

include "twitter.thrift"

/**
 * It's considered good form to declare an exception type for your service.
 * Thrift will serialize and transmit them transparently.
 */
exception WentworthException {
  1: string description
}

struct WNotification {
  1: i32 notification_type,
  2: i64 target_post_id,
  3: i64 from_tumblelog_id,
  4: i64 note_id,
  5: i64 new_post_id,
  6: optional i64 timestamp
}

service WentworthService extends twitter.ThriftService {

  list<WNotification> get(1: i64 tumblelogId, 2: i32 count) throws
    (1: WentworthException wwerror)

  void store(1: i64 tumblelogId, 2: WNotification notification) throws
    (1: WentworthException wwerror)

  void remove(1: i64 tumblelogId, 2: WNotification notification) throws
    (1: WentworthException wwerror)

  void removeAll(1: i64 tumblelogId) throws
    (1: WentworthException wwerror)
}
