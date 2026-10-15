-- Manual cutover only: pause producers/old relays and reconcile the Kafka backlog first.
BEGIN;
LOCK TABLE message_outbox IN SHARE ROW EXCLUSIVE MODE;
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM message_outbox WHERE kind='KAFKA' AND status='PENDING'
        AND (destination <> 'note-updates'
          OR payload::jsonb->>'eventId' IS DISTINCT FROM id::text
          OR payload::jsonb->>'noteID' IS DISTINCT FROM routing_key
          OR payload::jsonb->>'userID' IS NULL
          OR NULLIF(payload::jsonb->>'username','') IS NULL
          OR jsonb_typeof(payload::jsonb->'noteRequest') IS DISTINCT FROM 'object')
    ) THEN
        RAISE EXCEPTION 'Invalid legacy note command: reconcile manually; no rows converted';
    END IF;
    UPDATE message_outbox SET kind='NOTE_UPDATE',destination='notes.note-updates.v1',last_error=NULL
    WHERE kind='KAFKA' AND status='PENDING';
END $$;
COMMIT;
